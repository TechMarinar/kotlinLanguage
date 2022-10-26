/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.backend.common

import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrStatementOrigin
import org.jetbrains.kotlin.ir.expressions.impl.IrCallImpl
import org.jetbrains.kotlin.ir.util.IdSignature
import org.jetbrains.kotlin.ir.util.SymbolTable
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid

class IrActualizerTransformer(private val platformSymbolTable: SymbolTable) : IrElementTransformerVoid() {
    companion object {
        private val notExpectMask = IdSignature.Flags.IS_EXPECT.encode(true).inv()
    }

    override fun visitCall(expression: IrCall): IrExpression {
        val result = visitFunctionAccess(expression) as IrCall
        val actualSymbol = when (val signature = expression.symbol.signature) {
            is IdSignature.AccessorSignature -> {
                val propertySignature = unifySignatureIfExpect(signature.propertySignature as? IdSignature.CommonSignature)
                propertySignature?.let {
                    val propertySymbol = platformSymbolTable.referencePropertyIfAny(it) ?: reportMissingActual(it)
                    val owner = propertySymbol.owner
                    if (expression.origin == IrStatementOrigin.GET_PROPERTY) {
                        owner.getter!!.symbol
                    } else {
                        owner.setter!!.symbol
                    }
                }
            }
            is IdSignature.CommonSignature -> {
                val functionSignature = unifySignatureIfExpect(signature)
                functionSignature?.let {
                    platformSymbolTable.referenceSimpleFunctionIfAny(it) ?: reportMissingActual(it)
                }
            }
            else -> null
        }

        return actualSymbol?.let {
            IrCallImpl(
                result.startOffset,
                result.endOffset,
                result.type,
                actualSymbol,
                result.typeArgumentsCount,
                result.valueArgumentsCount,
                result.origin,
                result.superQualifierSymbol
            ).also {
                it.extensionReceiver = result.extensionReceiver
                it.dispatchReceiver = result.dispatchReceiver
                for (index in 0 until result.valueArgumentsCount) {
                    it.putValueArgument(index, result.getValueArgument(index))
                }
                for (index in 0 until result.typeArgumentsCount) {
                    it.putTypeArgument(index, result.getTypeArgument(index))
                }
            }
        } ?: result
    }

    private fun unifySignatureIfExpect(signature: IdSignature.CommonSignature?): IdSignature.CommonSignature? {
        return if (signature?.isExpect() == true)
            IdSignature.CommonSignature(
                signature.packageFqName,
                signature.declarationFqName,
                signature.id,
                signature.mask and notExpectMask
            )
        else
            null
    }

    private fun reportMissingActual(signature: IdSignature): Nothing {
        // TODO: Probably diagnostics should be reported here
        error("Expect member $signature should have corresponding actual member")
    }
}