package app.revanced.util

import app.revanced.patcher.data.BytecodeContext
import app.revanced.patcher.extensions.InstructionExtensions.addInstruction
import app.revanced.patcher.extensions.InstructionExtensions.addInstructions
import app.revanced.patcher.fingerprint.MethodFingerprint
import app.revanced.patcher.patch.PatchException
import app.revanced.patcher.util.proxy.mutableTypes.MutableClass
import app.revanced.patcher.util.proxy.mutableTypes.MutableMethod
import app.revanced.patches.shared.misc.mapping.ResourceMappingPatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.WideLiteralInstruction
import com.android.tools.smali.dexlib2.iface.reference.Reference
import com.android.tools.smali.dexlib2.util.MethodUtil
import org.stringtemplate.v4.compiler.Bytecode.instructions

fun MethodFingerprint.resultOrThrow() = result ?: throw exception

/**
 * The [PatchException] of failing to resolve a [MethodFingerprint].
 *
 * @return The [PatchException].
 */
val MethodFingerprint.exception
    get() = PatchException("Failed to resolve ${this.javaClass.simpleName}")

/**
 * Find the [MutableMethod] from a given [Method] in a [MutableClass].
 *
 * @param method The [Method] to find.
 * @return The [MutableMethod].
 */
fun MutableClass.findMutableMethodOf(method: Method) = this.methods.first {
    MethodUtil.methodSignaturesMatch(it, method)
}

/**
 * Apply a transform to all methods of the class.
 *
 * @param transform The transformation function. Accepts a [MutableMethod] and returns a transformed [MutableMethod].
 */
fun MutableClass.transformMethods(transform: MutableMethod.() -> MutableMethod) {
    val transformedMethods = methods.map { it.transform() }
    methods.clear()
    methods.addAll(transformedMethods)
}

/**
 * Inject a call to a method that hides a view.
 *
 * @param insertIndex The index to insert the call at.
 * @param viewRegister The register of the view to hide.
 * @param classDescriptor The descriptor of the class that contains the method.
 * @param targetMethod The name of the method to call.
 */
fun MutableMethod.injectHideViewCall(
    insertIndex: Int,
    viewRegister: Int,
    classDescriptor: String,
    targetMethod: String,
) = addInstruction(
    insertIndex,
    "invoke-static { v$viewRegister }, $classDescriptor->$targetMethod(Landroid/view/View;)V",
)

/**
 * Get the index of the first instruction with the id of the given resource name.
 *
 * Requires [ResourceMappingPatch] as a dependency.
 *
 * @param resourceName the name of the resource to find the id for.
 * @return the index of the first instruction with the id of the given resource name, or -1 if not found.
 * @throws PatchException if the resource cannot be found.
 * @see [indexOfIdResourceOrThrow], [indexOfFirstWideLiteralInstructionValueReversed]
 */
fun Method.indexOfIdResource(resourceName: String): Int {
    val resourceId = ResourceMappingPatch["id", resourceName]
    return indexOfFirstWideLiteralInstructionValue(resourceId)
}

/**
 * Get the index of the first instruction with the id of the given resource name or throw a [PatchException].
 *
 * Requires [ResourceMappingPatch] as a dependency.
 *
 * @throws [PatchException] if the resource is not found, or the method does not contain the resource id literal value.
 * @see [indexOfIdResource], [indexOfFirstWideLiteralInstructionValueReversedOrThrow]
 */
fun Method.indexOfIdResourceOrThrow(resourceName: String): Int {
    val index = indexOfIdResource(resourceName)
    if (index < 0) {
        throw PatchException("Found resource id for: '$resourceName' but method does not contain the id: $this")
    }

    return index
}

/**
 * Find the index of the first wide literal instruction with the given value.
 *
 * @return the first literal instruction with the value, or -1 if not found.
 * @see indexOfFirstWideLiteralInstructionValueOrThrow
 */
fun Method.indexOfFirstWideLiteralInstructionValue(literal: Long) = implementation?.let {
    it.instructions.indexOfFirst { instruction ->
        (instruction as? WideLiteralInstruction)?.wideLiteral == literal
    }
} ?: -1

/**
 * Find the index of the first wide literal instruction with the given value,
 * or throw an exception if not found.
 *
 * @return the first literal instruction with the value, or throws [PatchException] if not found.
 */
fun Method.indexOfFirstWideLiteralInstructionValueOrThrow(literal: Long): Int {
    val index = indexOfFirstWideLiteralInstructionValue(literal)
    if (index < 0) throw PatchException("Could not find literal value: $literal")
    return index
}

/**
 * Find the index of the last wide literal instruction with the given value.
 *
 * @return the last literal instruction with the value, or -1 if not found.
 * @see indexOfFirstWideLiteralInstructionValueOrThrow
 */
fun Method.indexOfFirstWideLiteralInstructionValueReversed(literal: Long) = implementation?.let {
    it.instructions.indexOfLast { instruction ->
        (instruction as? WideLiteralInstruction)?.wideLiteral == literal
    }
} ?: -1

/**
 * Find the index of the last wide literal instruction with the given value,
 * or throw an exception if not found.
 *
 * @return the last literal instruction with the value, or throws [PatchException] if not found.
 */
fun Method.indexOfFirstWideLiteralInstructionValueReversedOrThrow(literal: Long): Int {
    val index = indexOfFirstWideLiteralInstructionValueReversed(literal)
    if (index < 0) throw PatchException("Could not find literal value: $literal")
    return index
}

/**
 * Check if the method contains a literal with the given value.
 *
 * @return if the method contains a literal with the given value.
 */
fun Method.containsWideLiteralInstructionValue(literal: Long) =
    indexOfFirstWideLiteralInstructionValue(literal) >= 0

/**
 * Traverse the class hierarchy starting from the given root class.
 *
 * @param targetClass the class to start traversing the class hierarchy from.
 * @param callback function that is called for every class in the hierarchy.
 */
fun BytecodeContext.traverseClassHierarchy(targetClass: MutableClass, callback: MutableClass.() -> Unit) {
    callback(targetClass)
    this.findClass(targetClass.superclass ?: return)?.mutableClass?.let {
        traverseClassHierarchy(it, callback)
    }
}

/**
 * Get the [Reference] of an [Instruction] as [T].
 *
 * @param T The type of [Reference] to cast to.
 * @return The [Reference] as [T] or null
 * if the [Instruction] is not a [ReferenceInstruction] or the [Reference] is not of type [T].
 * @see ReferenceInstruction
 */
inline fun <reified T : Reference> Instruction.getReference() = (this as? ReferenceInstruction)?.reference as? T

/**
 * Get the index of the first [Instruction] that matches the predicate.
 *
 * @param predicate The predicate to match.
 * @return The index of the first [Instruction] that matches the predicate.
 */
// TODO: delete this on next major release, the overloaded method with an optional start index serves the same purposes.
// Method is deprecated, but annotation is commented out otherwise during compilation usage of the replacement is
// incorrectly flagged as deprecated.
// @Deprecated("Use the overloaded method with an optional start index.", ReplaceWith("indexOfFirstInstruction(predicate)"))
fun Method.indexOfFirstInstruction(predicate: Instruction.() -> Boolean) = indexOfFirstInstruction(0, predicate)

/**
 * Get the index of the first [Instruction] that matches the predicate, starting from [startIndex].
 *
 * @param startIndex Optional starting index to start searching from.
 * @return -1 if the instruction is not found.
 * @see indexOfFirstInstructionOrThrow
 */
fun Method.indexOfFirstInstruction(startIndex: Int = 0, predicate: Instruction.() -> Boolean): Int {
    val index = this.implementation!!.instructions.drop(startIndex).indexOfFirst(predicate)

    return if (index >= 0) {
        startIndex + index
    } else {
        -1
    }
}

/**
 * Get the index of the first [Instruction] that matches the predicate, starting from [startIndex].
 *
 * @return the index of the instruction
 * @throws PatchException
 * @see indexOfFirstInstruction
 */
fun Method.indexOfFirstInstructionOrThrow(startIndex: Int = 0, predicate: Instruction.() -> Boolean): Int {
    val index = indexOfFirstInstruction(startIndex, predicate)
    if (index < 0) {
        throw PatchException("Could not find instruction index")
    }
    return index
}

/**
 * @return The list of indices of the opcode in reverse order.
 */
fun Method.findOpcodeIndicesReversed(opcode: Opcode): List<Int> =
    findOpcodeIndicesReversed { this.opcode == opcode }

/**
 * @return The list of indices of the opcode in reverse order.
 */
fun Method.findOpcodeIndicesReversed(filter: Instruction.() -> Boolean): List<Int> {
    val indexes = implementation!!.instructions
        .withIndex()
        .filter { (_, instruction) -> filter(instruction) }
        .map { (index, _) -> index }
        .reversed()

    if (indexes.isEmpty()) throw PatchException("No matching instructions found in: $this")

    return indexes
}


/**
 * Return the resolved method early.
 */
fun MethodFingerprint.returnEarly(bool: Boolean = false) {
    val const = if (bool) "0x1" else "0x0"
    result?.let { result ->
        val stringInstructions = when (result.method.returnType.first()) {
            'L' ->
                """
                        const/4 v0, $const
                        return-object v0
                        """
            'V' -> "return-void"
            'I', 'Z' ->
                """
                        const/4 v0, $const
                        return v0
                        """
            else -> throw Exception("This case should never happen.")
        }

        result.mutableMethod.addInstructions(0, stringInstructions)
    } ?: throw exception
}

/**
 * Return the resolved methods early.
 */
fun Iterable<MethodFingerprint>.returnEarly(bool: Boolean = false) = forEach { fingerprint ->
    fingerprint.returnEarly(bool)
}

/**
 * Return the resolved methods early.
 */
@Deprecated("Use the Iterable version")
fun List<MethodFingerprint>.returnEarly(bool: Boolean = false) = forEach { fingerprint ->
    fingerprint.returnEarly(bool)
}

/**
 * Resolves this fingerprint using the classDef of a parent fingerprint.
 */
fun MethodFingerprint.alsoResolve(context: BytecodeContext, parentFingerprint: MethodFingerprint) =
    also { resolve(context, parentFingerprint.resultOrThrow().classDef) }.resultOrThrow()
