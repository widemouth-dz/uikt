package wedo.widemouth.compiler.generator

import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.ExperimentalKotlinPoetApi
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.LambdaTypeName
import com.squareup.kotlinpoet.MemberName
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeVariableName
import com.squareup.kotlinpoet.asTypeName

/**
 * @author WideMouth
 * @since 2023/6/3
 */
object DslGroupGenerator {

	private val mTasks = mapOf(
		"ScopeGroup" to DslGroupGenerator::buildScopeGroupFun,
		"Group" to DslGroupGenerator::buildGroupFun,
		"GroupWithDefaultLP" to DslGroupGenerator::buildGroupWithDefaultLPFun,
		"PartialAppliedGroup" to DslGroupGenerator::buildGroupPartialFun,
		"PartialAppliedGroupWithDefaultLP" to DslGroupGenerator::buildPartialGroupWithDefaultLPFun
	)

	fun generate(
		groups: Sequence<Pair<ClassName, ClassName>>, fileProcessor: (FileSpec) -> Unit,
	) {

		fileProcessor(buildGroupReceiverFile(groups))

		mTasks.forEach { task ->
			val fileBuilder = FileSpec.builder(sGeneratorPackage, task.key)
			groups.forEach { fileBuilder.addFunction(task.value(it.first, it.second)) }

			fileProcessor(fileBuilder.build())
		}
	}

	private fun buildGroupReceiverFile(groups: Sequence<Pair<ClassName, ClassName>>): FileSpec {
		val groupReceiverFileBuilder = FileSpec.scriptBuilder("GroupReceiver", sGeneratorPackage)
		groups.forEach {
			groupReceiverFileBuilder.addStatement(
				"typealias %LReceiver<SL> = context((@%T·%T),·(@%T·%T<%T>)) (@%T·SL).()·->·Unit",
				it.first.simpleName,
				sViewMarkerName,
				it.first,
				sScopeMarkerName,
				sScopeName,
				it.second,
				sLayoutMarkerName,
			).addCode("\n")
		}
		return groupReceiverFileBuilder.build()
	}

	private fun buildScopeGroupFun(groupName: ClassName, layoutParamsName: ClassName): FunSpec {
		val sGroupBlockParameter = ParameterSpec.builder(
			"block", groupName.receiverName.parameterizedBy(sScopeLayoutTypeVariableName)
		).build()

		return FunSpec.builder(groupName.simpleName)
			.addTypeVariable(sScopeLayoutWithBoundTypeVariableName)
			.receiver(sScopeName.parameterizedBy(sScopeLayoutTypeVariableName))
			.addParameter(sGroupBlockParameter)
			.returns(groupName)
			.addStatement(
				"return %M(%L,·%L,·%L)",
				sGroupFunName,
				groupName.constructorReference(),
				layoutParamsName.constructorReference(),
				sGroupBlockParameter.name
			).build()
	}

	private fun buildGroupFun(groupName: ClassName, layoutParamsName: ClassName): FunSpec {
		val sGroupBlockParameter = ParameterSpec.builder(
			"block", groupName.receiverName.parameterizedBy(sGroupLayoutTypeVariableName)
		).addModifiers(KModifier.NOINLINE).build()

		return FunSpec.builder(groupName.simpleName)
			.addModifiers(KModifier.INLINE)
			.addTypeVariable(sGroupLayoutWithBoundTypeVariableName)
			.addParameter(sContextParameter)
			.addParameter(sGroupLayoutBuilderParameter)
			.addParameter(sGroupBlockParameter)
			.returns(groupName)
			.addStatement(
				"return %M(%L,·%L,·%L,·%L,·%L)",
				sRootFunName,
				sContextParameter.name,
				groupName.constructorReference(),
				layoutParamsName.constructorReference(),
				sGroupLayoutBuilderParameter.name,
				sGroupBlockParameter.name
			).build()
	}

	private fun buildGroupWithDefaultLPFun(
		groupName: ClassName,
		layoutParamsName: ClassName,
	): FunSpec {
		val sGroupBlockParameter = ParameterSpec.builder(
			"block", groupName.receiverName.parameterizedBy(sMarginLPName)
		).build()

		return FunSpec.builder(groupName.simpleName)
			.addParameter(sContextParameter)
			.addParameter(sGroupBlockParameter)
			.returns(groupName)
			.addStatement(
				"return %M(%L,·%L,·%L,·%L,·%L)",
				sRootFunName,
				sContextParameter.name,
				groupName.constructorReference(),
				layoutParamsName.constructorReference(),
				sRootLayoutFunName.reference(),
				sGroupBlockParameter.name
			).build()
	}

	private fun buildGroupPartialFun(groupName: ClassName, layoutParamsName: ClassName): FunSpec {
		val groupBlockParameter = ParameterSpec.builder(
			"block", groupName.receiverName.parameterizedBy(sGroupLayoutTypeVariableName)
		).addModifiers(KModifier.NOINLINE).build()

		val groupLayoutBuilderParameter =
			sGroupLayoutBuilderParameter.toBuilder().addModifiers(KModifier.CROSSINLINE).build()

		return FunSpec.builder(groupName.simpleName)
			.addModifiers(KModifier.INLINE)
			.addTypeVariable(sGroupLayoutWithBoundTypeVariableName)
			.addParameter(groupLayoutBuilderParameter)
			.addParameter(groupBlockParameter)
			.returns(sScopeViewBuilderName.parameterizedBy(groupName))
			.addStatement(
				"return %M(%L,·%L,·%L,·%L)",
				sRootFunName,
				groupName.constructorReference(),
				layoutParamsName.constructorReference(),
				sGroupLayoutBuilderParameter.name,
				groupBlockParameter.name
			).build()
	}

	private fun buildPartialGroupWithDefaultLPFun(
		groupName: ClassName,
		layoutParamsName: ClassName,
	): FunSpec {
		val groupBlockParameter = ParameterSpec.builder(
			"block", groupName.receiverName.parameterizedBy(sMarginLPName)
		).build()

		return FunSpec.builder(groupName.simpleName)
			.addTypeVariable(sGroupLayoutWithBoundTypeVariableName)
			.addParameter(groupBlockParameter)
			.returns(sScopeViewBuilderName.parameterizedBy(groupName))
			.addStatement(
				"return %M(%L,·%L,·%L,·%L)",
				sRootFunName,
				groupName.constructorReference(),
				layoutParamsName.constructorReference(),
				sRootLayoutFunName.reference(),
				groupBlockParameter.name
			).build()
	}

	private val ClassName.receiverName
		get() = ClassName(sGeneratorPackage, "${simpleName}Receiver")

	private const val sUiktPackageName = "wedo.widemouth.uikt"
	private const val sGeneratorPackage = sUiktPackageName

	private val sLPName = ClassName(sUiktPackageName, "LP")

	private const val sGroupLayoutTypeVariable = "GL"
	private val sGroupLayoutTypeVariableName = TypeVariableName(sGroupLayoutTypeVariable)
	private val sGroupLayoutWithBoundTypeVariableName =
		TypeVariableName(sGroupLayoutTypeVariable, sLPName)

	private val sContextName = ClassName("android.content", "Context")
	private val sContextParameter = ParameterSpec.builder("ctx", sContextName).build()

	private val sLayoutBuilderName = ClassName(sUiktPackageName, "LayoutBuilder")
	private val sScopeViewBuilderName = ClassName(sUiktPackageName, "ScopeViewBuilder")

	private val sGroupLayoutName =
		sLayoutBuilderName.parameterizedBy(sGroupLayoutTypeVariableName)

	private val sGroupLayoutBuilderParameter =
		ParameterSpec.builder("groupLayoutBuilder", sGroupLayoutName).build()

	private val sScopeMarkerName = ClassName(sUiktPackageName, "ScopeMarker")
	private val sViewMarkerName = ClassName(sUiktPackageName, "ViewMarker")
	private val sLayoutMarkerName = ClassName(sUiktPackageName, "LayoutMarker")

	private val sRootFunName = MemberName(sUiktPackageName, "Root")
	private val sGroupFunName = MemberName(sUiktPackageName, "Group")
	private val sRootLayoutFunName = MemberName(sUiktPackageName, "rootLayout")

	private val sMarginLPName = ClassName(sUiktPackageName, "MarginLP")

	private val sScopeName = ClassName(sUiktPackageName, "Scope")
	private const val sScopeLayoutTypeVariable = "SL"
	private val sScopeLayoutTypeVariableName = TypeVariableName(sScopeLayoutTypeVariable)
	private val sScopeLayoutWithBoundTypeVariableName =
		TypeVariableName(sScopeLayoutTypeVariable, sLPName)


	@Deprecated(
		"contextReceivers with annotation is not supported in kotlinpoet",
		level = DeprecationLevel.HIDDEN
	)
	@OptIn(ExperimentalKotlinPoetApi::class)
	private val groupReceiverParameterBuilder =
		{ groupTypeName: TypeName, groupLayoutTypeName: TypeName, groupScopeLayoutTypeName: TypeName ->
			val groupLayoutType = groupLayoutTypeName.copy(
				annotations = listOf(AnnotationSpec.builder(sLayoutMarkerName).build())
			)
			val groupType = groupTypeName.copy(
				annotations = listOf(AnnotationSpec.builder(sViewMarkerName).build())
			)
			val scopeType = sScopeName.parameterizedBy(groupScopeLayoutTypeName).copy(
				annotations = listOf(AnnotationSpec.builder(sScopeMarkerName).build())
			)
			ParameterSpec.builder(
				"block",
				LambdaTypeName.get(
					receiver = groupLayoutType,
					returnType = Unit::class.asTypeName(),
					contextReceivers = listOf(groupType, scopeType)
				)
			).build()
		}

}