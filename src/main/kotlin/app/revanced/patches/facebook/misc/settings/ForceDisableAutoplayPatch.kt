package app.revanced.patches.facebook.misc.settings

import app.revanced.util.exception
import app.revanced.patcher.data.BytecodeContext
import app.revanced.patcher.extensions.InstructionExtensions.addInstructions
import app.revanced.patcher.patch.BytecodePatch
import app.revanced.patcher.patch.annotation.CompatiblePackage
import app.revanced.patcher.patch.annotation.Patch
import app.revanced.patches.facebook.misc.settings.fingerprints.AutoPlaySettingFingerprint

@Patch(
    name = "Force disable autoplay",
    description = "Forcefully disable autoplay of videos on feeds regardless of configuration",
    compatiblePackages = [CompatiblePackage("com.facebook.katana")]
)
@Suppress("unused")
object ForceDisableAutoplayPatch : BytecodePatch(setOf(AutoPlaySettingFingerprint)) {
    override fun execute(context: BytecodeContext) {
        AutoPlaySettingFingerprint.result?.apply {

            mutableMethod.addInstructions(0, """
                    const/4 v0, 0x0
                    return v0
            """)
        } ?: throw AutoPlaySettingFingerprint.exception
    }
}
