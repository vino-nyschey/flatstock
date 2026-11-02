package app.flatstock.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import app.flatstock.R
import app.flatstock.data.OpenFoodFacts
import app.flatstock.data.Product
import app.flatstock.data.Repository
import app.flatstock.data.Supply
import app.flatstock.data.Wg
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors

/** Full-screen camera that reports the first barcode it reads. */
@Composable
fun ScannerScreen(onBarcode: (String) -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) { if (!granted) launcher.launch(Manifest.permission.CAMERA) }
    BackHandler(onBack = onClose)

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (granted) {
            CameraPreview(onBarcode)
        } else {
            Column(
                Modifier.align(Alignment.Center).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(stringResource(R.string.camera_permission), color = Color.White, textAlign = TextAlign.Center)
                Button(onClick = { launcher.launch(Manifest.permission.CAMERA) }) { Text(stringResource(R.string.allow_camera)) }
            }
        }
        Box(
            Modifier
                .align(Alignment.Center)
                .fillMaxWidth(0.82f)
                .aspectRatio(1.6f)
                .border(3.dp, Color.White, RoundedCornerShape(16.dp))
        )
        IconButton(onClick = onClose, modifier = Modifier.align(Alignment.TopStart).safeDrawingPadding().padding(8.dp)) {
            Icon(Icons.Default.Close, stringResource(R.string.cancel), tint = Color.White)
        }
        Text(
            stringResource(R.string.scan_hint),
            color = Color.White,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.6f))
                .safeDrawingPadding()
                .padding(24.dp),
        )
    }
}

@Composable
private fun CameraPreview(onBarcode: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnBarcode by rememberUpdatedState(onBarcode)
    val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }

    DisposableEffect(lifecycleOwner) {
        val executor = Executors.newSingleThreadExecutor()
        val analyzer = BarcodeAnalyzer { currentOnBarcode(it) }
        val providerFuture = ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider? = null
        var disposed = false

        providerFuture.addListener({
            if (disposed) return@addListener
            val cameraProvider = providerFuture.get().also { provider = it }
            val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { it.setAnalyzer(executor, analyzer) }
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
        }, ContextCompat.getMainExecutor(context))

        onDispose {
            disposed = true
            provider?.unbindAll()
            analyzer.close()
            executor.shutdown()
        }
    }

    AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
}

/** Runs ML Kit on camera frames (on the phone, offline) and reports the first barcode once. */
private class BarcodeAnalyzer(private val onBarcode: (String) -> Unit) : ImageAnalysis.Analyzer {
    private val scanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8, Barcode.FORMAT_UPC_A, Barcode.FORMAT_UPC_E, Barcode.FORMAT_CODE_128)
            .build()
    )

    // ML Kit listeners run on the main thread, so this needs no locking.
    private var done = false

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(proxy: ImageProxy) {
        val media = proxy.image
        if (done || media == null) {
            proxy.close()
            return
        }
        scanner.process(InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees))
            .addOnSuccessListener { codes ->
                val code = codes.firstNotNullOfOrNull { c -> c.rawValue?.takeIf { it.isNotBlank() } }
                if (code != null && !done) {
                    done = true
                    onBarcode(code)
                }
            }
            .addOnCompleteListener { proxy.close() }
    }

    fun close() = scanner.close()
}

/**
 * After a scan: a known barcode shows its supply's stock ("Salt: 2 packs") with Bought / Used one.
 * An unknown one is looked up on Open Food Facts and the user picks which supply it counts towards,
 * so different brands and shops all add up under one supply.
 */
@Composable
fun ScanResultDialog(wg: Wg, barcode: String, supplies: List<Supply>, me: String, repo: Repository, onBought: (Supply) -> Unit, onClose: () -> Unit) {
    var loading by remember { mutableStateOf(true) }
    var product by remember { mutableStateOf<Product?>(null) }
    var suggestedName by rememberSaveable { mutableStateOf("") }
    var brand by remember { mutableStateOf("") }
    var linking by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(barcode) {
        val known = repo.productOnce(wg.id, barcode)
        product = known
        if (known == null || supplies.none { it.id == known.supplyId }) {
            OpenFoodFacts.lookup(barcode)?.let { suggestedName = it.name; brand = it.brand }
            linking = true
        }
        loading = false
    }

    val supply = supplies.firstOrNull { it.id == product?.supplyId }
    when {
        loading -> AlertDialog(
            onDismissRequest = onClose,
            title = { Text(stringResource(R.string.scan_looking_up)) },
            text = { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() } },
            confirmButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.cancel)) } },
        )
        linking || supply == null -> LinkProductDialog(
            suggestedName, brand, supplies,
            onLink = { supplyId ->
                repo.linkProduct(wg.id, barcode, supplyId, suggestedName, brand)
                product = Product(barcode, supplyId, suggestedName, brand)
                linking = false
            },
            onCreate = { name ->
                val id = repo.addSupply(wg.id, name)
                repo.linkProduct(wg.id, barcode, id, suggestedName.ifBlank { name }, brand)
                product = Product(barcode, id, suggestedName, brand)
                linking = false
            },
            onClose = onClose,
        )
        else -> AlertDialog(
            onDismissRequest = onClose,
            title = { Text(supply.name) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val p = product
                    if (p != null && p.name.isNotBlank() && p.name != supply.name) {
                        Text(
                            listOf(p.brand, p.name).filter { it.isNotBlank() }.joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Pill(levelLabel(supply.levelEnum), levelColor(supply.levelEnum))
                        if (supply.levelEnum != app.flatstock.domain.Level.FULL && supply.updatedBy.isNotBlank()) {
                            Text(wg.nameOf(supply.updatedBy), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    PackCounter(supply.count) { repo.changeCount(wg.id, supply, it) }
                    TextButton(onClick = { linking = true }) { Text(stringResource(R.string.scan_relink)) }
                }
            },
            confirmButton = { Button(onClick = { onBought(supply) }) { Text(stringResource(R.string.bought)) } },
            dismissButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.done)) } },
        )
    }
}

@Composable
private fun LinkProductDialog(
    productName: String,
    brand: String,
    supplies: List<Supply>,
    onLink: (String) -> Unit,
    onCreate: (String) -> Unit,
    onClose: () -> Unit,
) {
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var newName by rememberSaveable { mutableStateOf(productName) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(stringResource(R.string.scan_which_supply)) },
        text = {
            Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
                Text(
                    if (productName.isNotBlank()) listOf(brand, productName).filter { it.isNotBlank() }.joinToString(" · ")
                    else stringResource(R.string.scan_unknown_product),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    stringResource(R.string.scan_which_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                supplies.forEach { s ->
                    Row(
                        Modifier.fillMaxWidth().clickable { selected = s.id },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = selected == s.id, onClick = { selected = s.id })
                        Text(s.name)
                    }
                }
                Row(Modifier.fillMaxWidth().clickable { selected = "" }, verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = selected == "", onClick = { selected = "" })
                    Text(stringResource(R.string.scan_new_supply))
                }
                if (selected == "") {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text(stringResource(R.string.name)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { selected?.let { if (it.isEmpty()) onCreate(newName) else onLink(it) } },
                enabled = selected != null && (selected != "" || newName.isNotBlank()),
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { OutlinedButton(onClick = onClose, colors = ButtonDefaults.outlinedButtonColors()) { Text(stringResource(R.string.cancel)) } },
    )
}
