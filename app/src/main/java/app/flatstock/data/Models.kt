package app.flatstock.data

import app.flatstock.domain.Expense
import app.flatstock.domain.Level
import app.flatstock.domain.Payment
import app.flatstock.domain.SupplyState
import com.google.firebase.firestore.Exclude

/** A shared flat (WG). Former members stay in [memberEmails]/[memberNames] so old purchases keep their names. */
data class Wg(
    @get:Exclude val id: String = "",
    val name: String = "",
    val ownerUid: String = "",
    val members: List<String> = emptyList(),
    val memberEmails: Map<String, String> = emptyMap(),
    val memberNames: Map<String, String> = emptyMap(),
    val createdAt: Long = 0L,
) {
    /** Display name: the chosen name, else the part of the email before the @. */
    fun nameOf(uid: String): String =
        memberNames[uid]?.takeIf { it.isNotBlank() }
            ?: memberEmails[uid]?.substringBefore('@')
            ?: "?"
}

/** One shared household good, e.g. dishwasher tabs. */
data class Supply(
    @get:Exclude val id: String = "",
    val name: String = "",
    val level: String = Level.FULL.name,
    /** uid of whoever last changed the level, and when. */
    val updatedBy: String = "",
    val updatedAt: Long = 0L,
) {
    @get:Exclude val levelEnum: Level get() = Level.of(level)

    fun toState() = SupplyState(name, levelEnum, updatedBy, updatedAt)
}

/** Someone bought something; [amountCents] is split equally among [sharedBy]. 0 means no cost recorded. */
data class Purchase(
    @get:Exclude val id: String = "",
    val supplyId: String = "",
    val itemName: String = "",
    val paidBy: String = "",
    val amountCents: Long = 0L,
    val sharedBy: List<String> = emptyList(),
    val createdBy: String = "",
    val createdAt: Long = 0L,
) {
    fun toExpense() = Expense(paidBy, amountCents, sharedBy)
}

/** A "settle up" payment: [fromUid] paid [toUid]. */
data class SettlePayment(
    @get:Exclude val id: String = "",
    val fromUid: String = "",
    val toUid: String = "",
    val amountCents: Long = 0L,
    val createdBy: String = "",
    val createdAt: Long = 0L,
) {
    fun toPayment() = Payment(fromUid, toUid, amountCents)
}

data class Invite(
    @get:Exclude val id: String = "",
    val wgId: String = "",
    val wgName: String = "",
    val email: String = "",
    val invitedBy: String = "",
    val status: String = "pending",
    val createdAt: Long = 0L,
)

/** The WGs a user belongs to and the one currently shown. */
data class UserGroups(val groupIds: List<String>, val activeId: String?, val name: String)
