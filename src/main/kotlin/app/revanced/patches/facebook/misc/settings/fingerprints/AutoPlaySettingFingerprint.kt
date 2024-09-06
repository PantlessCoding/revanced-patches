package app.revanced.patches.facebook.misc.settings.fingerprints

import app.revanced.patcher.extensions.or
import app.revanced.patcher.fingerprint.MethodFingerprint
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction31i

internal object AutoPlaySettingFingerprint : MethodFingerprint(
    customFingerprint = { methodDef, classDef ->
                // All methods within initialise a generic model with the same instructions apart from the value of those two constants
                classDef.type == "Lcom/facebook/feed/autoplay/AutoplayStateManager;" &&
                methodDef.name == "canAutoplay"
    },
)