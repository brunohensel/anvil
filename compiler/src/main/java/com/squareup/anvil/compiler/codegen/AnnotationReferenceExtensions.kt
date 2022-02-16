package com.squareup.anvil.compiler.codegen

import com.squareup.anvil.compiler.internal.argumentType
import com.squareup.anvil.compiler.internal.classDescriptor
import com.squareup.anvil.compiler.internal.findAnnotationArgument
import com.squareup.anvil.compiler.internal.getAnnotationValue
import com.squareup.anvil.compiler.internal.reference.AnnotationReference
import com.squareup.anvil.compiler.internal.reference.AnnotationReference.Descriptor
import com.squareup.anvil.compiler.internal.reference.AnnotationReference.Psi
import com.squareup.anvil.compiler.internal.reference.AnvilCompilationExceptionAnnotationReference
import com.squareup.anvil.compiler.internal.reference.ClassReference
import com.squareup.anvil.compiler.internal.reference.toClassReference
import com.squareup.anvil.compiler.internal.requireFqName
import org.jetbrains.kotlin.psi.KtClassLiteralExpression
import org.jetbrains.kotlin.resolve.constants.KClassValue

internal fun AnnotationReference.parentScope(): ClassReference {
  return when (this) {
    is Psi ->
      annotation
        .findAnnotationArgument<KtClassLiteralExpression>(
          name = "parentScope",
          index = 1
        )
        ?.requireFqName(module)
        ?.toClassReference(module)

    is Descriptor ->
      (annotation.getAnnotationValue("parentScope") as? KClassValue)
        ?.argumentType(module)
        ?.classDescriptor()
        ?.toClassReference(module)
  } ?: throw AnvilCompilationExceptionAnnotationReference(
    message = "Couldn't find parentScope for $fqName.",
    annotationReference = this
  )
}