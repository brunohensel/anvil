package com.squareup.anvil.compiler.codegen

import com.google.common.truth.Truth.assertThat
import com.squareup.anvil.annotations.MergeComponent
import com.squareup.anvil.annotations.MergeSubcomponent
import com.squareup.anvil.annotations.compat.MergeModules
import com.squareup.anvil.compiler.AnyDaggerComponent
import com.squareup.anvil.compiler.MODULE_PACKAGE_PREFIX
import com.squareup.anvil.compiler.anyDaggerComponent
import com.squareup.anvil.compiler.compile
import com.squareup.anvil.compiler.componentInterface
import com.squareup.anvil.compiler.componentInterfaceAnvilModule
import com.squareup.anvil.compiler.contributingInterface
import com.squareup.anvil.compiler.daggerModule
import com.squareup.anvil.compiler.daggerModule1
import com.squareup.anvil.compiler.parentInterface
import com.squareup.anvil.compiler.subcomponentInterface
import com.squareup.anvil.compiler.subcomponentInterfaceAnvilModule
import com.tschuchort.compiletesting.KotlinCompilation.ExitCode.COMPILATION_ERROR
import dagger.Binds
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters
import java.lang.reflect.Modifier.isAbstract
import kotlin.reflect.KClass

@RunWith(Parameterized::class)
class BindingModuleGeneratorTest(
  private val annotationClass: KClass<*>
) {

  private val annotation = "@${annotationClass.simpleName}"
  private val import = "import ${annotationClass.java.canonicalName}"

  companion object {
    @Parameters(name = "{0}")
    @JvmStatic fun annotationClasses(): Collection<Any> {
      return listOf(MergeComponent::class, MergeSubcomponent::class, MergeModules::class)
    }
  }

  @Test fun `a Dagger module is generated for a merged class and added to the component`() {
    compile(
        """
        package com.squareup.test
        
        $import
        
        $annotation(Any::class)
        interface ComponentInterface
    """
    ) {
      val modules = if (annotationClass == MergeModules::class) {
        componentInterface.daggerModule.includes.toList()
      } else {
        componentInterface.anyDaggerComponent.modules
      }
      assertThat(modules).containsExactly(componentInterfaceAnvilModule.kotlin)
    }
  }

  @Test fun `the Anvil module is merged with other modules`() {
    compile(
        """
        package com.squareup.test
        
        import com.squareup.anvil.annotations.ContributesTo
        $import
        
        @ContributesTo(Any::class)
        @dagger.Module
        abstract class DaggerModule1
        
        $annotation(Any::class)
        interface ComponentInterface
    """
    ) {
      val modules = if (annotationClass == MergeModules::class) {
        componentInterface.daggerModule.includes.toList()
      } else {
        componentInterface.anyDaggerComponent.modules
      }
      assertThat(modules)
          .containsExactly(componentInterfaceAnvilModule.kotlin, daggerModule1.kotlin)
    }
  }

  @Test fun `there is an Anvil module for each component`() {
    compile(
        """
        package com.squareup.test

        import com.squareup.anvil.annotations.ContributesTo
        $import

        $annotation(Any::class)
        interface ComponentInterface

        $annotation(Unit::class)
        interface SubcomponentInterface
    """
    ) {
      if (annotationClass == MergeModules::class) {
        assertThat(componentInterface.daggerModule.includes.toList())
            .containsExactly(componentInterfaceAnvilModule.kotlin)
        assertThat(subcomponentInterface.daggerModule.includes.toList())
            .containsExactly(subcomponentInterfaceAnvilModule.kotlin)
      } else {
        assertThat(componentInterface.anyDaggerComponent.modules)
            .containsExactly(componentInterfaceAnvilModule.kotlin)
        assertThat(subcomponentInterface.anyDaggerComponent.modules)
            .containsExactly(subcomponentInterfaceAnvilModule.kotlin)
      }
    }
  }

  @Test fun `Anvil modules for inner classes are generated`() {
    compile(
        """
        package com.squareup.test

        import com.squareup.anvil.annotations.ContributesTo
        $import
        
        class SomeClass {
          $annotation(Any::class)
          interface ComponentInterface
        }
    """
    ) {
      val className =
        "$MODULE_PACKAGE_PREFIX.com.squareup.test.SomeClassComponentInterfaceAnvilModule"
      assertThat(classLoader.loadClass(className)).isNotNull()
    }
  }

  @Test fun `the Dagger binding method is generated`() {
    compile(
        """
        package com.squareup.test
        
        import com.squareup.anvil.annotations.ContributesBinding
        $import

        interface ParentInterface
        
        interface Middle : ParentInterface

        @ContributesBinding(Any::class, ParentInterface::class)
        interface ContributingInterface : Middle
        
        $annotation(Any::class)
        interface ComponentInterface
    """
    ) {
      val modules = if (annotationClass == MergeModules::class) {
        componentInterface.daggerModule.includes.toList()
      } else {
        componentInterface.anyDaggerComponent.modules
      }
      assertThat(modules).containsExactly(componentInterfaceAnvilModule.kotlin)

      val methods = modules.first().java.declaredMethods
      assertThat(methods).hasLength(1)

      with(methods[0]) {
        assertThat(returnType).isEqualTo(parentInterface)
        assertThat(parameterTypes.toList()).containsExactly(contributingInterface)
        assertThat(isAbstract(modifiers)).isTrue()
        assertThat(isAnnotationPresent(Binds::class.java)).isTrue()
      }
    }
  }

  @Test fun `the bound type is implied when there is only one super type`() {
    compile(
        """
        package com.squareup.test
        
        import com.squareup.anvil.annotations.ContributesBinding
        $import

        interface ParentInterface

        @ContributesBinding(Any::class)
        interface ContributingInterface : ParentInterface
        
        $annotation(Any::class)
        interface ComponentInterface
    """
    ) {
      val modules = if (annotationClass == MergeModules::class) {
        componentInterface.daggerModule.includes.toList()
      } else {
        componentInterface.anyDaggerComponent.modules
      }
      assertThat(modules).containsExactly(componentInterfaceAnvilModule.kotlin)

      val methods = modules.first().java.declaredMethods
      assertThat(methods).hasLength(1)

      with(methods[0]) {
        assertThat(returnType).isEqualTo(parentInterface)
        assertThat(parameterTypes.toList()).containsExactly(contributingInterface)
        assertThat(isAbstract(modifiers)).isTrue()
        assertThat(isAnnotationPresent(Binds::class.java)).isTrue()
      }
    }
  }

  @Test fun `the bound type can only be implied with on super type (2 interfaces)`() {
    compile(
        """
        package com.squareup.test
        
        import com.squareup.anvil.annotations.ContributesBinding
        $import

        interface ParentInterface
        
        @ContributesBinding(Any::class)
        interface ContributingInterface : ParentInterface, CharSequence
        
        $annotation(Any::class)
        interface ComponentInterface
    """
    ) {
      assertThat(exitCode).isEqualTo(COMPILATION_ERROR)

      assertThat(messages).contains("Source.kt: (9, 11)")
      assertThat(messages).contains(
          "com.squareup.test.ContributingInterface contributes a binding, but does not specify " +
              "the bound type. This is only allowed with one direct super type. If there are " +
              "multiple, then the bound type must be explicitly defined in the " +
              "@ContributesBinding annotation."
      )
    }
  }

  @Test fun `the bound type can only be implied with on super type (class and interface)`() {
    compile(
        """
        package com.squareup.test
        
        import com.squareup.anvil.annotations.ContributesBinding
        $import

        interface ParentInterface
        
        open class Abc
        
        @ContributesBinding(Any::class)
        interface ContributingInterface : Abc(), ParentInterface
        
        $annotation(Any::class)
        interface ComponentInterface
    """
    ) {
      assertThat(exitCode).isEqualTo(COMPILATION_ERROR)

      assertThat(messages).contains("Source.kt: (11, 11)")
      assertThat(messages).contains(
          "com.squareup.test.ContributingInterface contributes a binding, but does not specify " +
              "the bound type. This is only allowed with one direct super type. If there are " +
              "multiple, then the bound type must be explicitly defined in the " +
              "@ContributesBinding annotation."
      )
    }
  }

  @Test fun `the contributed binding class must extend the bound type`() {
    compile(
        """
        package com.squareup.test
        
        import com.squareup.anvil.annotations.ContributesBinding
        $import

        interface ParentInterface

        @ContributesBinding(Any::class, ParentInterface::class)
        interface ContributingInterface
        
        $annotation(Any::class)
        interface ComponentInterface
    """
    ) {
      assertThat(exitCode).isEqualTo(COMPILATION_ERROR)

      assertThat(messages).contains("Source.kt: (9, 11)")
      assertThat(messages).contains(
          "com.squareup.test.ContributingInterface contributes a binding for " +
              "com.squareup.test.ParentInterface, but doesn't extend this type."
      )
    }
  }

  private val Class<*>.anyDaggerComponent: AnyDaggerComponent
    get() = anyDaggerComponent(annotationClass)
}
