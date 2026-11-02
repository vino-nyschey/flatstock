package app.flatstock.data

import android.util.Log
import app.flatstock.domain.Level
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

private const val TAG = "Repository"

/**
 * Single access point to Firebase.
 *
 * Firestore layout (web/js/db.js uses the same one):
 *   users/{uid}                   { email, name, groupIds[], activeGroupId }
 *   invites/{wgId}_{email}        { wgId, wgName, email, invitedBy, status, createdAt }
 *   wgs/{wid}                     { name, ownerUid, members[], memberEmails{uid: email}, memberNames{uid: name}, createdAt }
 *   wgs/{wid}/supplies/{id}       Supply
 *   wgs/{wid}/purchases/{id}      Purchase
 *   wgs/{wid}/payments/{id}       SettlePayment
 *
 * Supply level changes are fire-and-forget: Firestore applies them locally at once and syncs when
 * the phone is online again. Money entries are awaited so the user sees if they failed.
 */
class Repository {
    val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()

    val uid: String get() = auth.currentUser?.uid.orEmpty()
    val currentEmail: String get() = auth.currentUser?.email.orEmpty().lowercase()

    private fun users() = db.collection("users")
    private fun invites() = db.collection("invites")
    private fun wg(wid: String) = db.collection("wgs").document(wid)
    private fun supplies(wid: String) = wg(wid).collection("supplies")
    private fun purchases(wid: String) = wg(wid).collection("purchases")
    private fun payments(wid: String) = wg(wid).collection("payments")

    // region Account

    fun authState(): Flow<FirebaseUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser) }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    suspend fun signIn(email: String, password: String) {
        auth.signInWithEmailAndPassword(email.trim().lowercase(), password).await()
    }

    suspend fun register(name: String, email: String, password: String) {
        val user = auth.createUserWithEmailAndPassword(email.trim().lowercase(), password).await().user ?: return
        users().document(user.uid)
            .set(mapOf("email" to user.email?.lowercase(), "name" to name.trim()), SetOptions.merge())
            .await()
        user.sendEmailVerification().await()
    }

    suspend fun resendVerification() {
        auth.currentUser?.sendEmailVerification()?.await()
    }

    /** Re-reads the user from Firebase; returns true once the email link was clicked. */
    suspend fun reloadUser(): Boolean {
        auth.currentUser?.reload()?.await() ?: return false
        val verified = auth.currentUser?.isEmailVerified == true
        // Refresh the token so security rules see email_verified = true.
        if (verified) auth.currentUser?.getIdToken(true)?.await()
        return verified
    }

    suspend fun resetPassword(email: String) {
        auth.sendPasswordResetEmail(email.trim().lowercase()).await()
    }

    fun signOut() = auth.signOut()

    // endregion
    // region WG and invitations

    fun userGroupsFlow(uid: String): Flow<UserGroups> = callbackFlow {
        val reg = users().document(uid).addSnapshotListener { snap, err ->
            if (err != null) {
                Log.w(TAG, "user listen failed", err)
                return@addSnapshotListener
            }
            // An empty cache says nothing yet; wait for the server answer.
            if (snap == null || (snap.metadata.isFromCache && !snap.exists())) return@addSnapshotListener
            trySend(snap.toUserGroups())
        }
        awaitClose { reg.remove() }
    }

    /** Emits null when the WG can't be read (deleted, or the user left it). */
    fun wgFlow(wid: String): Flow<Wg?> = callbackFlow {
        val reg = wg(wid).addSnapshotListener { snap, err ->
            if (err != null) {
                Log.w(TAG, "wg listen failed", err)
                trySend(null)
                return@addSnapshotListener
            }
            trySend(snap?.toObject(Wg::class.java)?.copy(id = wid))
        }
        awaitClose { reg.remove() }
    }

    fun pendingInvites(email: String): Flow<List<Invite>> =
        invites()
            .whereEqualTo("email", email.lowercase())
            .whereEqualTo("status", "pending")
            .listFlow { it.toObject(Invite::class.java)?.copy(id = it.id) }

    /** Invitations this WG sent that nobody accepted yet. */
    fun sentInvites(wid: String): Flow<List<Invite>> =
        invites()
            .whereEqualTo("wgId", wid)
            .whereEqualTo("status", "pending")
            .listFlow { it.toObject(Invite::class.java)?.copy(id = it.id) }

    private suspend fun myName(): String =
        runCatching { users().document(uid).get().await().getString("name") }.getOrNull().orEmpty()

    /** Creates the WG with a starter list of supplies ([starterSupplies], in the user's language). */
    suspend fun createWg(name: String, starterSupplies: List<String>) {
        val ref = db.collection("wgs").document()
        ref.set(
            mapOf(
                "name" to name.trim(),
                "ownerUid" to uid,
                "members" to listOf(uid),
                "memberEmails" to mapOf(uid to currentEmail),
                "memberNames" to mapOf(uid to myName()),
                "createdAt" to System.currentTimeMillis(),
            )
        ).await()
        val batch = db.batch()
        starterSupplies.forEach { batch.set(supplies(ref.id).document(), newSupply(it)) }
        batch.commit().await()
        joinedWg(ref.id)
    }

    private suspend fun joinedWg(wid: String) {
        users().document(uid).set(
            mapOf("email" to currentEmail, "groupIds" to FieldValue.arrayUnion(wid), "activeGroupId" to wid),
            SetOptions.merge(),
        ).await()
    }

    suspend fun acceptInvite(invite: Invite) {
        // The join rule checks email_verified; a token issued before the email was confirmed still says false.
        val user = auth.currentUser ?: throw EmailNotVerifiedException()
        user.reload().await()
        if (user.getIdToken(true).await().claims["email_verified"] != true) throw EmailNotVerifiedException()
        wg(invite.wgId).update(
            mapOf(
                "members" to FieldValue.arrayUnion(uid),
                "memberEmails.$uid" to currentEmail,
                "memberNames.$uid" to myName(),
            )
        ).await()
        joinedWg(invite.wgId)
        invites().document(invite.id).update("status", "accepted").await()
    }

    suspend fun declineInvite(invite: Invite) {
        invites().document(invite.id).update("status", "declined").await()
    }

    suspend fun createInvite(group: Wg, inviteeEmail: String) {
        val email = inviteeEmail.trim().lowercase()
        invites().document("${group.id}_$email").set(
            mapOf(
                "wgId" to group.id,
                "wgName" to group.name,
                "email" to email,
                "invitedBy" to currentEmail,
                "status" to "pending",
                "createdAt" to System.currentTimeMillis(),
            )
        ).await()
    }

    suspend fun cancelInvite(invite: Invite) {
        invites().document(invite.id).delete().await()
    }

    suspend fun setMyName(wid: String, name: String) {
        wg(wid).update("memberNames.$uid", name.trim()).await()
        users().document(uid).set(mapOf("name" to name.trim()), SetOptions.merge()).await()
    }

    suspend fun renameWg(wid: String, name: String) {
        wg(wid).update("name", name.trim()).await()
    }

    /**
     * Leaves [wid]. The name and email stay in the WG so past purchases still show who paid;
     * open balances stay too and can still be settled by the others.
     */
    suspend fun leaveWg(wid: String, allIds: List<String>) {
        wg(wid).update("members", FieldValue.arrayRemove(uid)).await()
        val next = allIds.firstOrNull { it != wid }
        users().document(uid).update(
            mapOf(
                "groupIds" to FieldValue.arrayRemove(wid),
                "activeGroupId" to (next ?: FieldValue.delete()),
            )
        ).await()
    }

    // endregion
    // region Supplies

    fun suppliesFlow(wid: String): Flow<List<Supply>> =
        supplies(wid)
            .listFlow { it.toObject(Supply::class.java)?.copy(id = it.id) }
            .map { list -> list.sortedBy { it.name.lowercase() } }

    private fun newSupply(name: String) = mapOf(
        "name" to name.trim(),
        "level" to Level.FULL.name,
        "updatedBy" to uid,
        "updatedAt" to System.currentTimeMillis(),
    )

    fun addSupply(wid: String, name: String) {
        supplies(wid).add(newSupply(name)).logFailure()
    }

    fun renameSupply(wid: String, id: String, name: String) {
        supplies(wid).document(id).update("name", name.trim()).logFailure()
    }

    fun deleteSupply(wid: String, id: String) {
        supplies(wid).document(id).delete().logFailure()
    }

    fun setLevel(wid: String, id: String, level: Level) {
        supplies(wid).document(id).update(
            mapOf("level" to level.name, "updatedBy" to uid, "updatedAt" to System.currentTimeMillis())
        ).logFailure()
    }

    // endregion
    // region Purchases and settling up

    fun purchasesFlow(wid: String): Flow<List<Purchase>> =
        purchases(wid)
            .listFlow { it.toObject(Purchase::class.java)?.copy(id = it.id) }
            .map { list -> list.sortedByDescending { it.createdAt } }

    fun paymentsFlow(wid: String): Flow<List<SettlePayment>> =
        payments(wid)
            .listFlow { it.toObject(SettlePayment::class.java)?.copy(id = it.id) }
            .map { list -> list.sortedByDescending { it.createdAt } }

    /** Records the purchase and marks the supply as full again, in one write. */
    suspend fun recordPurchase(wid: String, supply: Supply?, itemName: String, amountCents: Long, paidBy: String, sharedBy: List<String>) {
        val now = System.currentTimeMillis()
        val batch = db.batch()
        batch.set(
            purchases(wid).document(),
            mapOf(
                "supplyId" to supply?.id.orEmpty(),
                "itemName" to itemName.trim(),
                "paidBy" to paidBy,
                "amountCents" to amountCents,
                "sharedBy" to sharedBy.distinct(),
                "createdBy" to uid,
                "createdAt" to now,
            ),
        )
        if (supply != null) {
            batch.update(
                supplies(wid).document(supply.id),
                mapOf("level" to Level.FULL.name, "updatedBy" to uid, "updatedAt" to now),
            )
        }
        batch.commit().await()
    }

    suspend fun recordPayment(wid: String, fromUid: String, toUid: String, amountCents: Long) {
        payments(wid).add(
            mapOf(
                "fromUid" to fromUid,
                "toUid" to toUid,
                "amountCents" to amountCents,
                "createdBy" to uid,
                "createdAt" to System.currentTimeMillis(),
            )
        ).await()
    }

    /** Only the person who entered a purchase or payment may delete it (see firestore.rules). */
    suspend fun deletePurchase(wid: String, id: String) {
        purchases(wid).document(id).delete().await()
    }

    suspend fun deletePayment(wid: String, id: String) {
        payments(wid).document(id).delete().await()
    }

    // endregion
    // region One-shot reads for the background reminder

    suspend fun activeGroupOnce(uid: String): String? =
        users().document(uid).get().await().toUserGroups().activeId

    suspend fun wgOnce(wid: String): Wg? =
        wg(wid).get().await().toObject(Wg::class.java)?.copy(id = wid)

    suspend fun suppliesOnce(wid: String): List<Supply> =
        supplies(wid).get().await().documents.mapNotNull { it.toObject(Supply::class.java)?.copy(id = it.id) }

    // endregion
}

private fun <T : Any> Query.listFlow(map: (DocumentSnapshot) -> T?): Flow<List<T>> = callbackFlow {
    val reg = addSnapshotListener { snap, err ->
        if (err != null) {
            Log.w(TAG, "listen failed", err)
            return@addSnapshotListener
        }
        trySend(snap?.documents?.mapNotNull(map).orEmpty())
    }
    awaitClose { reg.remove() }
}

private fun DocumentSnapshot.toUserGroups(): UserGroups {
    @Suppress("UNCHECKED_CAST")
    val ids = (get("groupIds") as? List<String>).orEmpty()
    val active = getString("activeGroupId")?.takeIf { it in ids } ?: ids.firstOrNull()
    return UserGroups(ids, active, getString("name").orEmpty())
}

/** Thrown when joining a WG before the email address is confirmed. */
class EmailNotVerifiedException : Exception("email not verified")

private fun <T> Task<T>.logFailure() {
    addOnFailureListener { Log.w(TAG, "write failed", it) }
}
