# UIKT Arch Doc

## KSP

### ViewEffectKsp
UIKT使用KSP在编译时为注解的View组件生成Ext类，Ext继承了IViewEffect接口，由ViewEffectHelper委托实现ViewEffect属性。

通过注解ViewEffectWidget注册
```kotlin
@file:ViewEffectWidget(
    [
        ConstraintLayout::class,
        FrameLayout::class,
        RelativeLayout::class,
        LinearLayout::class,
        CoordinatorLayout::class,

        AppCompatImageView::class,
        ImageButton::class,
        ImageView::class
    ]
)
```
以上注册信息会生成以下代码文件
+ decoration
  - AppCompatImageViewExt
  - ConstraintLayoutExt
  - CoordinatorLayoutExt
  - FrameLayoutExt
  - ImageButtonExt
  - ImageViewExt
  - LinearLayoutExt
  - RelativeLayoutExt

### DslKsp
View组件的DSL构建函数同样由KSP在编译时生成，通过以下注解注册。
```kotlin

@file:DslWidget(
    [
        TextView::class,
        Button::class,
        ImageButton::class,
        ImageView::class,
    ]
)

@file:DslGroup(
    [
        FrameLayout::class,
        RelativeLayout::class,
        LinearLayout::class,
        CoordinatorLayout::class,

        ScrollView::class,
        HorizontalScrollView::class,
        NestedScrollView::class
    ]
)

```
生成的代码文件
+ uikt
  - Group.kt
  - GroupReceiver.kt
  - GroupWithDefaultLP.kt
  - PartialAppliedGroup.kt
  - PartialAppliedGroupWithDefaultLP.kt
  - PartialAppliedWidget.kt
  - PartialAppliedWidgetWithDefaultLP.kt
  - ScopeGroup.kt
  - ScopeWidget.kt
  - Widget.kt
  - WidgetWithDefaultLP.kt

对于ViewGroup，需要找出其LayoutParams类，这在源码上无法处理，需要手动添加。 元编程能够做到，以下是KSP实现。
```kotlin

val layoutBaseClass =
  resolver.getClassDeclarationByName("android.view.ViewGroup.LayoutParams")
    ?.asStarProjectedType()
    ?: error("Can not find class [android.view.ViewGroup.LayoutParams] in your compiled module")

group.allNestedClasses.first { layoutBaseClass.isAssignableFrom(it.asStarProjectedType()) }

val KSClassDeclaration.allNestedClasses: Sequence<KSClassDeclaration>
    get() = declarations.filterIsInstance<KSClassDeclaration>() +
            getAllSuperTypes()
                .map { it.declaration }
                .filterIsInstance<KSClassDeclaration>()
                .flatMap { it.declarations }
                .filterIsInstance<KSClassDeclaration>()
```

### [Multiple round processing](https://kotlinlang.org/docs/ksp-multi-round.html)
多轮编译是处理器都需要考虑的情况，apt，kapt，ksp都有相关的API支持，以下是ksp示例。
```kotlin
override fun process(resolver: Resolver): List<KSAnnotated> {
    val symbols = resolver.getSymbolsWithAnnotation("com.example.annotation.Builder")
    val result = symbols.filter { !it.validate() }
    symbols
        .filter { it is KSClassDeclaration && it.validate() }
        .map { it.accept(BuilderVisitor(), Unit) }
    return result
}
```
本轮无法编译的符号集合可以return给process函数，延迟到下轮处理。

但是貌似不支持读取Annotation的无效符号（而是直接过滤掉）。考虑以下情况：
```kotlin
@file:DslGroupDeferred(
    [
        FrameLayoutExt::class,
        RelativeLayoutExt::class,
        ConstraintLayoutExt::class,
        LinearLayoutExt::class,
        CoordinatorLayoutExt::class,
    ]
)
```
无法通过validate判断Annotation是否引用了无效符号（即将生成的符号），所以无法判断是否需要延迟到下一轮处理。
这里使用DslGroupDeferred注解注册延迟处理的组件，如果引用符号的数量为0（被过滤掉），则延迟处理。需要注意的是，
DslGroupDeferred引用的延迟符号需要是同一批生成的符号，这在增量编译时会存在一些问题。

## Functionalization

UIKT的基础高度函数化。

下面是UIKT的一个基础函数，参数和返回值都是函数类型。

```kotlin
typealias ViewBuilder<V> = (ctx: Context) -> V
typealias LayoutBuilder<L> = () -> L
typealias ScopeViewBuilder<V> = ViewBuilder<V>

        /** Partial applied function of `Group(ctx, ...)`. */
@SinceKotlin(ContextReceiverGenericSinceKotlin)
inline fun <G : ViewGroup, GL : LP, GSL : LP> Root(
    crossinline groupBuilder: ViewBuilder<G>,
    noinline scopeLayoutBuilder: LayoutBuilder<GSL>,
    crossinline groupLayoutBuilder: LayoutBuilder<GL>,
    noinline block: @LayoutMarker context((@ViewMarker G), (@ScopeMarker Scope<GSL>)) GL.() -> Unit,
): ScopeViewBuilder<G> = { ctx ->
    Group(ctx, groupBuilder, scopeLayoutBuilder, groupLayoutBuilder, block)
}
```
下面是一个调用实例，分别传入了四个函数参数，这样做的好处是: 
- 参数并不需要传入真正的实例，只需要将构造方法的函数引用作为参数，比如，`::View`、`::layout`.
使函数调用足够简单，同时语义更加地清晰。
- UIKT的扩展可以很方便地进行，到此，UIKT已经可以很好地工作了，你可以像这样来套用任意View组件。
- 甚至可以替代泛型反射实现实例化，且不附带反射的性能开销。
```kotlin
    val homePage = root(context)
    val detailPage = root(context)
    
    val root = Root(::_Box, ::boxLayout, ::marginLayout) {
    
    }  
```

## [Context receivers](https://github.com/Kotlin/KEEP/blob/context-receivers/proposals/context-receivers.md)

UIKT使用`context receivers`来构建布局DSL，这是一个**实验性**API。
视图布局块中至少有两个 receiver，其布局和自身。但是`extension function`只支持一个 receiver，我尝试了很多可能的方法来解决它。
以下是尝试列表：

* 多个 `receiver`, 多个 `extension function`, 就像这样:

  ```
  lambda:  view.() -> Unit, layout.() -> Unit
  
  dsl:     view{ }, layout{ }
  ```

  [Anko-Layouts](https://github.com/Kotlin/anko/wiki/Anko-Layouts) 遵循它，一个 view 带有两个 block，如下所示：
  
  ```
  view{
    
  }.lparams{
  
  }
  ```

* 一个 `extension function` 带有一个 `receiver` 和多个参数, 就像这样:

  ```
  lambda: view.(layout) -> Unit
  
  dsl:    view{ layout-> }
  ```

  如果是这样，必须使用块中的参数前缀调用其属性，就像`XML`那样。
  
  ```
      view{ layout->
         layout.*** =
         layout.*** =
      }
  ```
它们都能够满足构建布局树的要求，但有一点瑕疵。如果它适用于多个`receiver`，这将是最佳实践。

起初，我去找了一些关于 lambda 的想法，这是一个尝试：

```
lambda: view.() -> layout.() -> Unit

dsl:    view{
              {
                ..block..
              }
          }
```

现在，内部`block`有两个隐式 receiver。之后，我发现了一个关于多个 receiver 的实验性弱关键字`context`。

在我看来，与嵌套 `lambda` 没有区别，由于其实验性质，有些东西与其他一些功能不兼容，它们是： 
- lambda 块必须是 `noinline`。
- 当有两个以上的 receiver 时，不能与`typealias`一起使用。

一些详细信息链接：

- [KEEP-259](https://github.com/Kotlin/KEEP/blob/context-receivers/proposals/context-receivers.md#detailed-design)
- [KT-51234](https://youtrack.jetbrains.com/issue/KT-51234/Context-receivers-can-be-duplicated-in-function-declaration)
- [KT-54233](https://youtrack.jetbrains.com/issue/KT-54233/Lambda-context-receiver-definitions-can-no-longer-accept-multiple-reified-generic-context-parameters)

然后，必须将具有三个 receiver 的函数类型定义为冗余的 `lambda`，并且所有这些都没有内联功能。无论如何，我们已经有多个 receiver 。

```kotlin
typealias BoxReceiver<SL> = @LayoutMarker context((@ViewMarker _Box), (@ScopeMarker Scope<BoxLP>)) SL.() -> Unit
typealias ConstraintReceiver<SL> = @LayoutMarker context((@ViewMarker _Constraint), (@ScopeMarker Scope<ConstraintLP>)) SL.() -> Unit
typealias RelativeReceiver<SL> = @LayoutMarker context((@ViewMarker _Relative), (@ScopeMarker Scope<RelativeLP>)) SL.() -> Unit
typealias ColumnReceiver<SL> = @LayoutMarker context((@ViewMarker _Column), (@ScopeMarker Scope<LinearLP>)) SL.() -> Unit
typealias RowReceiver<SL> = ColumnReceiver<SL>

typealias ViewBuilder<V> = (ctx: Context) -> V
typealias LayoutBuilder<L> = () -> L
typealias ScopeViewBuilder<V> = ViewBuilder<V>

@SinceKotlin(ContextReceiverSinceKotlin)
typealias WidgetReceiver<V, L> = Receiver2<@ViewMarker V, @ScopeMarker @LayoutMarker L>
// Not supported since `Kotlin 1.7.20`.
// typealias GroupReceiver<G, SL, L> = Receiver3<@ViewMarker G, @ScopeMarker SL, @LayoutMarker L>

@SinceKotlin(ContextReceiverSinceKotlin)
typealias Receiver2<R1, R2> = context(R1) R2.() -> Unit
// Not supported since `Kotlin 1.7.20`.
// typealias Receiver3<R1, R2, R3> = context(R1, R2) R3.() -> Unit
```

## [DslMarker]
另一个重要的注意事项是 [DslMarker]，我们不仅要考虑来自外部块的隐式 receiver ，还要考虑当前块中的多个 receiver ，
一个带有上下文 receiver 的块要求可以访问其内部隐式 receiver ，但不能访问外部，因此内部隐式 receiver 绑定不同的 [markers][DslMarker]。
see [ViewMarker], [LayoutMarker], [ScopeMarker].

## Partial applied function

构造布局至少需要两个参数（请参阅下面的代码 1）、[context][Context] 和树`block`，至少对于布局根而言。

```kotlin
Root(context, block)         // 1

PartialRoot(block)(context)  // 2
```

我想用上面的`code2`构建它，[Root] 就是这样工作的。

`PartialRoot` function:
```kotlin
fun PartialRoot(block): (Context) -> View = { ctx ->
    Root(ctx, block)
}
```
Widget偏应用函数和原函数
```kotlin
/** Partial applied function of `Widget(ctx, ...)`. */
@SinceKotlin(ContextReceiverGenericSinceKotlin)
inline fun <V : View, VL : LP> Widget(
    crossinline viewBuilder: ViewBuilder<V>,
    crossinline widgetLayoutBuilder: LayoutBuilder<VL>,
    noinline block: WidgetReceiver<V, VL>,
): ViewBuilder<V> = { ctx -> Widget(ctx, viewBuilder, widgetLayoutBuilder, block) }

@SinceKotlin(ContextReceiverGenericSinceKotlin)
inline fun <V : View, VL : LP> Widget(
    ctx: Context,
    crossinline viewBuilder: ViewBuilder<V>,
    crossinline widgetLayoutBuilder: LayoutBuilder<VL>,
    noinline block: WidgetReceiver<V, VL>,
): V {
    val widget = viewBuilder(ctx)
    val widgetLayout = widgetLayoutBuilder()
    block(widget, widgetLayout)
    widget.layoutParams = widgetLayout
    return widget
}
```

## Constructor memory
对于自定义视图，构建时需要使用反射获取实例，这里通过`remember`实现记忆，减少反射开销。
```kotlin
@PublishedApi
internal fun <P, V> remember(
	keySelector: (P) -> Any? = { it },
	cache: MutableMap<Any?, V> = mutableMapOf(),
	function: (P) -> V,
): (P) -> V {
    return { cache.getOrPut(keySelector(it)) { function(it) } }
}

@PublishedApi
internal val sViewConstructor: (Class<*>) -> Constructor<*> = remember {
	it.getConstructor(Context::class.java)
}

@PublishedApi
internal val sLayoutConstructor: (Class<*>) -> Constructor<*> = remember {
	it.getConstructor(Int::class.java, Int::class.java)
}

@PublishedApi
internal inline fun <reified V : View> viewConstructor(context: Context): V =
	sViewConstructor(V::class.java).newInstance(context) as V

@PublishedApi
internal inline fun <reified L : LP> layoutConstructor(
	width: Int = WRAP_CONTENT, height: Int = WRAP_CONTENT
): L = sLayoutConstructor(L::class.java).newInstance(width, height) as L
```