# UIKt

## About

UIkt 是关于 Android UI 的`DSL`，它可以使用简单易读的 Kotlin 代码创建 UI。

这里有一个简短的介绍，[UIKT-vs-others](UIKT-vs-others.md)，如果你对`Android UI DSL`还不太了解，那么根据兼容性和你的喜好选择其中一个。

## Requirement

### Kotlin

|      Feature      | Kotlin  |
|:-----------------:|:-------:|
|     `context`     | v1.6.20 |
| `context` generic | v1.7.20 |
| `context` module  | v1.7.22 |

### Plugins

以下是 Kotlin 升级建议， [Gradle | Kotlin Doc](https://kotlinlang.org/docs/gradle-configure-project.html).

| Kotlin  | KGP     | Gradle |  AGP   |
|:-------:|---------|:------:|:------:|
| v1.7.22 | v1.7.22 | v6.7.1 | v4.0.1 |

## Configuration

配置以下`kotlinOptions`以启用`context`API。

```
kotlinOptions {
    freeCompilerArgs = freeCompilerArgs + ["-Xcontext-receivers"]
}
```

## Sample
请参阅 [Sample](./Sample.md) 了解详情.
```
Column {

    Row {
        height = 100.dp
        
        Text {
            text = "Title"
            textSize = 16f
        }
        
        Button {
            text = "OK"
            textSize = 16f
        }
    }
    
    Image {
        size = 100.dp
        src = R.drawable.ic_launcher_foreground
    }
}
```
## Others about `UIKT`

- [Arch Doc](./Arch.md): 实现细节。
- [Sample Doc](./Arch.md): 详细用例。
- [UIKT-vs-others](./UIKT-vs-others.md): UIKT 与其他相关项目的比较。