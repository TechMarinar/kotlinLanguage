/*
 * Copyright 2010-2018 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package kotlin.reflect.jvm.internal

import org.jetbrains.kotlin.descriptors.CallableMemberDescriptor
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.load.java.descriptors.JavaCallableMemberDescriptor
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.lang.reflect.WildcardType
import java.util.*
import kotlin.coroutines.Continuation
import kotlin.reflect.*
import kotlin.reflect.jvm.internal.calls.Caller
import kotlin.reflect.jvm.javaType
import kotlin.reflect.jvm.jvmErasure
import java.lang.reflect.Array as ReflectArray

internal abstract class KCallableImpl<out R> : KCallable<R>, KTypeParameterOwnerImpl {
    abstract val descriptor: CallableMemberDescriptor

    // The instance which is used to perform a positional call, i.e. `call`
    abstract val caller: Caller<*>

    // The instance which is used to perform a call "by name", i.e. `callBy`
    abstract val defaultCaller: Caller<*>?

    abstract val container: KDeclarationContainerImpl

    abstract val isBound: Boolean

    private val _annotations = ReflectProperties.lazySoft { descriptor.computeAnnotations() }

    override val annotations: List<Annotation> get() = _annotations()

    private val _parameters = ReflectProperties.lazySoft {
        val descriptor = descriptor
        val result = ArrayList<KParameter>()
        var index = 0

        if (!isBound) {
            val instanceReceiver = descriptor.instanceReceiverParameter
            if (instanceReceiver != null) {
                result.add(KParameterImpl(this, index++, KParameter.Kind.INSTANCE) { instanceReceiver })
            }

            val extensionReceiver = descriptor.extensionReceiverParameter
            if (extensionReceiver != null) {
                result.add(KParameterImpl(this, index++, KParameter.Kind.EXTENSION_RECEIVER) { extensionReceiver })
            }
        }

        for (i in descriptor.valueParameters.indices) {
            result.add(KParameterImpl(this, index++, KParameter.Kind.VALUE) { descriptor.valueParameters[i] })
        }

        // Constructor parameters of Java annotations are not ordered in any way, we order them by name here to be more stable.
        // Note that positional call (via "call") is not allowed unless there's a single non-"value" parameter,
        // so the order of parameters of Java annotation constructors here can be arbitrary
        if (isAnnotationConstructor && descriptor is JavaCallableMemberDescriptor) {
            result.sortBy { it.name }
        }

        result.trimToSize()
        result
    }

    override val parameters: List<KParameter>
        get() = _parameters()

    private val _returnType = ReflectProperties.lazySoft {
        KTypeImpl(descriptor.returnType!!) {
            extractContinuationArgument() ?: caller.returnType
        }
    }

    override val returnType: KType
        get() = _returnType()

    private val _typeParameters = ReflectProperties.lazySoft {
        descriptor.typeParameters.map { descriptor -> KTypeParameterImpl(this, descriptor) }
    }

    override val typeParameters: List<KTypeParameter>
        get() = _typeParameters()

    override val visibility: KVisibility?
        get() = descriptor.visibility.toKVisibility()

    override val isFinal: Boolean
        get() = descriptor.modality == Modality.FINAL

    override val isOpen: Boolean
        get() = descriptor.modality == Modality.OPEN

    override val isAbstract: Boolean
        get() = descriptor.modality == Modality.ABSTRACT

    protected val isAnnotationConstructor: Boolean
        get() = name == "<init>" && container.jClass.isAnnotation

    @Suppress("UNCHECKED_CAST")
    override fun call(vararg args: Any?): R = reflectionCall {
        return caller.call(args) as R
    }

    override fun callBy(args: Map<KParameter, Any?>): R {
        return if (isAnnotationConstructor) callAnnotationConstructor(args) else callDefaultMethod(args, null)
    }

    private val _parameterSizeWithContinuation = ReflectProperties.lazySoft {
        parameters.size + (if (isSuspend) 1 else 0)
    }

    private val _absentArguments = ReflectProperties.lazySoft {
        val parameterSize = _parameterSizeWithContinuation()
        val maskSize = (parameters.size + Integer.SIZE - 1) / Integer.SIZE

        // arguments with masks and DefaultConstructorMarker or MethodHandle
        // +1 is argument for DefaultConstructorMarker or MethodHandle
        val arguments = arrayOfNulls<Any?>(parameterSize + maskSize + 1)

        // set absent values
        parameters.forEach { parameter ->
            if (parameter.isOptional && !parameter.type.isInlineClassType) {
                // For inline class types, the javaType refers to the underlying type of the inline class,
                // but we have to pass null in order to mark the argument as absent for InlineClassAwareCaller.
                arguments[parameter.index] = defaultPrimitiveValue(parameter.type.javaType)
            } else if (parameter.isVararg) {
                arguments[parameter.index] = defaultEmptyArray(parameter.type)
            }
        }

        for (i in 0 until maskSize) {
            arguments[parameterSize + i] = 0
        }

        arguments
    }

    private fun getAbsentArguments(): Array<Any?> = _absentArguments().clone()

    // See ArgumentGenerator#generate
    internal fun callDefaultMethod(args: Map<KParameter, Any?>, continuationArgument: Continuation<*>?): R {
        val parameters = parameters
        val parameterSize = _parameterSizeWithContinuation()

        val arguments = getAbsentArguments().apply {
            if (isSuspend) {
                // continuationArgument is tail of arguments
                this[parameters.size] = continuationArgument
            }
        }

        var valueParameterIndex = 0
        var anyOptional = false

        // Optimize the most common case of 1 mask (32 or fewer default arguments) by avoiding boxing when computing mask value.
        val isSingleMask = parameters.size <= Integer.SIZE
        var singleMask = 0

        for (parameter in parameters) {
            when {
                args.containsKey(parameter) -> {
                    arguments[parameter.index] = args[parameter]
                }
                // Absent value is already set at _absentArguments
                parameter.isOptional -> {
                    val flag = 1 shl (valueParameterIndex % Integer.SIZE)
                    if (isSingleMask) {
                        singleMask = singleMask or flag
                    } else {
                        val maskIndex = parameterSize + (valueParameterIndex / Integer.SIZE)
                        arguments[maskIndex] = arguments[maskIndex] as Int or flag
                    }
                    anyOptional = true
                }
                parameter.isVararg -> {}
                else -> {
                    throw IllegalArgumentException("No argument provided for a required parameter: $parameter")
                }
            }

            if (parameter.kind == KParameter.Kind.VALUE) {
                valueParameterIndex++
            }
        }

        if (!anyOptional) {
            // The process is the same as call,
            // but it is called directly to avoid the processing cost of spread operator.
            @Suppress("UNCHECKED_CAST")
            return caller.call(arguments.copyOf(parameterSize)) as R
        }

        if (isSingleMask) {
            arguments[parameterSize] = singleMask
        }

        val caller = defaultCaller ?: throw KotlinReflectionInternalError("This callable does not support a default call: $descriptor")

        @Suppress("UNCHECKED_CAST")
        return reflectionCall {
            caller.call(arguments) as R
        }
    }

    private fun callAnnotationConstructor(args: Map<KParameter, Any?>): R {
        val arguments = parameters.map { parameter ->
            when {
                args.containsKey(parameter) -> {
                    args[parameter] ?: throw IllegalArgumentException("Annotation argument value cannot be null ($parameter)")
                }
                parameter.isOptional -> null
                parameter.isVararg -> defaultEmptyArray(parameter.type)
                else -> throw IllegalArgumentException("No argument provided for a required parameter: $parameter")
            }
        }

        val caller = defaultCaller ?: throw KotlinReflectionInternalError("This callable does not support a default call: $descriptor")

        @Suppress("UNCHECKED_CAST")
        return reflectionCall {
            caller.call(arguments.toTypedArray()) as R
        }
    }

    private fun defaultEmptyArray(type: KType): Any =
        type.jvmErasure.java.run {
            if (isArray) ReflectArray.newInstance(componentType, 0)
            else throw KotlinReflectionInternalError(
                "Cannot instantiate the default empty array of type $simpleName, because it is not an array type"
            )
        }

    private fun extractContinuationArgument(): Type? {
        if ((descriptor as? FunctionDescriptor)?.isSuspend == true) {
            // kotlin.coroutines.Continuation<? super java.lang.String>
            val continuationType = caller.parameterTypes.lastOrNull() as? ParameterizedType
            if (continuationType?.rawType == Continuation::class.java) {
                // ? super java.lang.String
                val wildcard = continuationType.actualTypeArguments.single() as? WildcardType
                // java.lang.String
                return wildcard?.lowerBounds?.first()
            }
        }

        return null
    }
}
