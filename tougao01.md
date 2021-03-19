>不知道你们经历过这种问题没有，比如问你遇到过线上性能问题没有，GC频繁，CPU飙高，任务队列积压，线程池任务拒绝等等，对于看重项目经验的面试官，这种问题基本是标配，问线上问题处理的经过，问题定位，排查的思路，怎么做的业务快速止血。一方面考察候选人项目的真实性，一般遇到线上问题大部分时候是系统主要负责人着手处理的，所以如果你处理过线上问题，也从侧面反映你的重要性。另外还能知道面试者是不是真的从原理上掌握了问题的根本原因，对技术的热忱等。另外建议大家处理完线上问题，排查了原因之后不要停，把排查过程和问题原因记录下来，一方面以后遇到类似问题可以基于已有的经验快速反应，另外就是一定让自己的知识**体系化**，怎么体系化呢? 就需要把日常的技术问题做归类总结。

这篇文章主要是给大家一个参考，讲讲安琪拉在上家公司遇到的线上问题以及排查思路，你们可能会好奇为什么不讲在阿里遇到的线上问题，这个我也会讲，但是因为很多数据要脱敏（马赛克），所以暂时先讲之前的，这篇是很早之前写的了，发布在博客园上。

铺垫太长了，下面进入正题。

### 问题

一个在普通不过的周末，安琪拉在家里玩安琪拉，蹲在草丛正准备给妲己一顿暴击，手机突然收到线上告警短信，吓得我一激灵，二技能放空，被妲己一套带走，趁着复活间隙，赶紧从床上爬起来打开电脑。

起因:  消息队列积压了十几万消息，第一反应，什么情况。。

![在这里插入图片描述](https://img-blog.csdnimg.cn/20210319015106385.png?x-oss-process=image/watermark,type_ZmFuZ3poZW5naGVpdGk,shadow_10,text_aHR0cHM6Ly9ibG9nLmNzZG4ubmV0L3poZW5nd2FuZ3p3,size_16,color_FFFFFF,t_70)


赶紧穿衣服，对待线上问题要严肃。

如下图，“待处...” 那一列就是指队列中“待处理的”消息。

消息积压，指的是消息的消费速度跟不上生产的速度，消息在消息队列中。

![img](https://img-blog.csdnimg.cn/img_convert/b3193c6bc9ed5a23f658377fb37ed06c.png)

可以看到有好几个分区已经积压了上万条消息；



### 问题排查

立即开始问题排查，遇到线上问题，一定是保证最快速度止血，降低对业务的影响，然后再是排查原因，当然有的问题也需要快速找到原因。

第一反应是不是入口流量太大，处理消息的线程池核心线程数满了，任务都在排队，但是看了入口流量并没有尖刺。

看监控的消息消费任务耗时，如下图：

![img](https://img-blog.csdnimg.cn/img_convert/c7e9ed3802baa09a9243107a8f157501.png)

可以看到耗时在不断增加。那就需要看处理耗时增加原因了，为什么处理任务的耗时上涨了。

查看消息消费日志，如下：

![img](https://img-blog.csdnimg.cn/img_convert/f48b8c5b1d81bbc6835d171365a151af.png)

发现有很多网络接口超时的。

![img](https://img-blog.csdnimg.cn/img_convert/a4f756f13ead1ce12345b0cded87a6e5.png)

大致得出结论：消息处理任务依赖下游系统接口，连接下游接口超时，连接下游接口设置的超时时间不算短，为什么下游接口如此多SocketTimeOut呢？

![img](https://img-blog.csdnimg.cn/img_convert/71b4891e7c831c9871ab15b6308e2b55.png)



下游系统也是我负责的系统，那重点开始看下游的系统监控，发现相关的接口调用的单机耗时时间极不规律，如下图所示：

![img](https://img-blog.csdnimg.cn/img_convert/8891cda3cdd1f41e9bf0024a489710f0.png)

对比一下日常这个接口的耗时时间，如下图，日常都没有超过100ms的：

![img](https://img-blog.csdnimg.cn/img_convert/248c84cb52ed470e209accaf217031e1.png)

查看下游系统的监控大盘，发现了问题：

![img](https://img-blog.csdnimg.cn/img_convert/b00a32a847f5c24fa4b07dc3651d3c43.png)

老年代GC次数暴涨，而且gc耗时都到了秒级别，1分钟5～10秒，太恐怖了。



### 分析GC问题

找一台机器，把GC回收dump下来分析，使用mat查看，如下图所示：

![img](https://img-blog.csdnimg.cn/img_convert/83e3f13cd6b05f4ebe5625497b59d534.png)

一共七百多M空间，一个对象就占了640M空间，找到原因了，有内鬼（大对象）。

![在这里插入图片描述](https://img-blog.csdnimg.cn/20210319015601282.png?x-oss-process=image/watermark,type_ZmFuZ3poZW5naGVpdGk,shadow_10,text_aHR0cHM6Ly9ibG9nLmNzZG4ubmV0L3poZW5nd2FuZ3p3,size_16,color_FFFFFF,t_70#pic_center)


继续往下，看这个对象为什么会这么大，从GC Roots最短路径如下，MAT的使用，以及JVM相关分析，感兴趣可以微信公众号【安琪拉的博客】回复JVM，拉你进JVM交流群。

![img](https://img-blog.csdnimg.cn/img_convert/82992b9f9ec921f9fc0c0200061b5b22.png)

解释下，上面主要有三列，第一列是内存对象的类，重点在2，3列，Shallow Heap指的是对象本身占据的内存大小， Retained Heap = 本身本身占据内存大小 + 当前对象可直接或间接引用到的对象的大小总和，换句话说，就是当前对象如果被回收，能够回收的内存大小。

继续看，第一步，查看谁引用了这个对象，找到自己代码中的类，

![img](https://img-blog.csdnimg.cn/img_convert/464517381abb021b019910ee5fa46f07.png)

第二步，查看这个对象TaggedMetricRegistry都引用了谁，为什么会占用这么大的内存空间，如下图所示，

![img](https://img-blog.csdnimg.cn/img_convert/eb00c06081aefa32e49c62a922987a7c.png)

找到罪魁祸首了，metrics这个 `ConcurrentHashMap` 占了671M内存，现在开始可以看下代码，找到 TaggedMetricRegistry继承自MetricRegistry，metrics 是MetricRegistry的成员变量，如下图：

![img](https://img-blog.csdnimg.cn/img_convert/ad513644060ad4a5bd4bf7a6a587c1d4.png)

![img](https://img-blog.csdnimg.cn/img_convert/eb8f05b99df20d5f81443474877c9ff3.png)



那为什么这个 `ConcurrentHashMap` 占了这么大的内存空间，并且GC也回收不掉呢？

我们继续看MAT，分析 `ConcurrentHashMap` 占有的详细内存分布：

![img](https://img-blog.csdnimg.cn/img_convert/0a5d0c7a8b84173b06c47290436e7c54.png)

发现`ConcurrentHashMap`有几个Node节点尤其大，

追下去，继续

![img](https://img-blog.csdnimg.cn/img_convert/dc61b5cf7db21456db622230695caaef.png)

看到这个key，对应在代码中的位置，

![img](https://img-blog.csdnimg.cn/img_convert/1c56648db3cd2d9b09506adec26f87d2.png)

 你们可能好奇这段代码是干嘛的呢？

这个代码的作用是统计接口每次的执行时间，它内部update的源码如下：

![在这里插入图片描述](https://img-blog.csdnimg.cn/20210319015322482.png?x-oss-process=image/watermark,type_ZmFuZ3poZW5naGVpdGk,shadow_10,text_aHR0cHM6Ly9ibG9nLmNzZG4ubmV0L3poZW5nd2FuZ3p3,size_16,color_FFFFFF,t_70#pic_center)


这个方法是统计接口的耗时、调用次数的，它内部有一个measurements 的跳跃表，存放时间戳和统计指标（耗时、调用次数）的键值对，设置的时间窗口是1分钟，也就是它会存放1分钟的统计数据在内存中，当然这里面有个采样比，不是1分钟的全量数据，可以看到采样比是COLLISION_BUFFER决定的，然后1分钟上报一次内存数据到远端。问题就出现在这，因为这个耗时统计的函数的QPS非常高，1分钟有数据频繁产生的时候，会导致在一个时间窗口（1分钟）measurements极速增长，导致内存占用快速增长，但是因为有强引用，GC的时候也不会把这个回收掉，所有才出现了上面的那个情况。

> 我不敢保证所写的每一句话都对，但都是一字一句用心讲述，一个简单的技术人。

