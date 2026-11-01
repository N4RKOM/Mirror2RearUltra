# Compile-only Xposed API

`xposed-api-82.jar` is the published Xposed API 82 artifact:
https://api.xposed.info/de/robv/android/xposed/api/82/api-82.jar

SHA-256: `f48c635f1c7469fdec0e00ad2ea0b7a6b2f5b55065784a35b7ca3a84615e8e25`

It must remain `compileOnly`: Vector/LSPosed supplies the runtime implementation.
Handwritten compile stubs must not be used instead: their `findAndHookMethod` return
descriptor was `Object` instead of `XC_MethodHook.Unhook`, causing NoSuchMethodError.
