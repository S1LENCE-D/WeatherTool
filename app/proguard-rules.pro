# WeatherTool 混淆规则
# release 构建已启用 R8 裁剪（minifyEnabled）+ 资源收缩（shrinkResources）。
# 工程当前为纯 Java + 框架 View，无反射/序列化依赖，故暂无 keep 规则。
# 若将来引入 JSON 反射映射（如 Gson 数据类）或 JNI，在此补 keep 规则。
