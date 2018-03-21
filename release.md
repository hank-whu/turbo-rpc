# 发布说明

----------------------------------------------

## turbo-0.0.4
发布时间: 2018-03-21

更新说明: 
1. 升级 netty 到 4.1.22.Final
2. 升级 guava 到 24.1
3. 升级 typesafe-config 到 1.3.3
4. 升级 jctools 到 2.1.2
5. 提高 ConcurrentArrayList ConcurrentIntToIntArrayMap ConcurrentIntToObjectArrayMap 线程安全性
6. ConcurrentIntToIntArrayMap ConcurrentIntToObjectArrayMap 添加 remove 方法
7. 引入 netty Recycler，提高性能，降低垃圾回收压力

----------------------------------------------

## turbo-0.0.3
发布时间: 2018-02-06

更新说明: 
1. 清理掉无用的 BlazeObjectPool 实现
2. 删除 RandomId 中的无用方法
3. 删除 ByteBufUtils 中的无用方法
4. 升级 jackson 到 2.9.4
5. 升级 guava 到 24.0
6. 优化 weight 相同情况下的 LoadBalance 性能
7. 修复重复创建 MethodParamClass 的 bug
8. 修复 App 被关掉后继续自动重连的 bug

----------------------------------------------

***END***