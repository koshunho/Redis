# Redis

## RedisConfig
RedisAutoConfiguration自动配置类中在容器中生成了一个RedisTemplate和一个StringRedisTemplate。

Jedis和Lettuce都是 Redis的Java客户端，提供了比较全面的Redis命令的支持。

SpringBoot默认采用Lettuce，所以只有LettuceConnectionConfiguration生效。

点进JedisConnectionConfiguration查看发现里面都是爆红的，好多类没有注入，所以不生效。
```java
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(RedisOperations.class)
@EnableConfigurationProperties(RedisProperties.class)
@Import({ LettuceConnectionConfiguration.class, JedisConnectionConfiguration.class })
public class RedisAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean(name = "redisTemplate")
	public RedisTemplate<Object, Object> redisTemplate(RedisConnectionFactory redisConnectionFactory)
			throws UnknownHostException {
		RedisTemplate<Object, Object> template = new RedisTemplate<>();
		template.setConnectionFactory(redisConnectionFactory);
		return template;
	}

	@Bean
	@ConditionalOnMissingBean
	public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory redisConnectionFactory)
			throws UnknownHostException {
		StringRedisTemplate template = new StringRedisTemplate();
		template.setConnectionFactory(redisConnectionFactory);
		return template;
	}

}

```
但是，这个RedisTemplate的泛型是<Object,Object>，写代码不方便，需要写好多类型转换的代码；我们需要一个泛型为<String,Object>形式的RedisTemplate。

并且，**这个RedisTemplate没有设置数据存在Redis时，key及value的序列化方式**。

**这个RedisTemplate没有设置数据存在Redis时，key及value的序列化方式**

**这个RedisTemplate没有设置数据存在Redis时，key及value的序列化方式**

看到这个@ConditionalOnMissingBean注解后，就知道如果Spring容器中有了RedisTemplate对象了，这个自动配置的RedisTemplate不会实例化。因此我们可以直接自己写个配置类，配置RedisTemplate。

在自己写的RedisTemplate中，key 和 hash的key 都采用String的序列化方式。

value 和 hash的value 都采用jackson的序列化方式。

这样就可以解决掉在redis-cli出现乱码的问题。

P.S. 但是在redis-cli中，get key时还是会出现乱码。查了说在启动时输入redis-cli.exe **--raw** 可以解决，但还是乱码- -
最后发现是cmd的编码问题，在启动之前切换到utf-8编码就好：**CHCP 65001**

此外，原本配置template中的enableDefaultTyping()过期了，因此改用mapper.activateDefaultTyping(mapper.getPolymorphicTypeValidator());

## RedisUtils
直接用RedisTemplate操作Redis，需要很多行代码，因此直接封装好一个
RedisUtils。

可以获取RedisConnection对象，但是很少用
```java
        RedisConnection connection = redisTemplate.getConnectionFactory().getConnection();
        connection.flushAll();
        connection.flushDb();
        connection.getSentinelConnection();
        connection.hyperLogLogCommands();
```
## 常见问题

#### 为什么Redis是单线程

因为Redis是基于内存的操作，CPU不是Redis的瓶颈，Redis的瓶颈最有可能是机器内存的大小or网络带宽。既然单线程容易实现，而且CPU不会成为瓶颈，那就顺理成章的采用单线程方案了。

p.s.最新版本已经支持多线程

**Redis的多线程部分只是用来处理网络数据的读写和协议解析，执行命令仍是单线程**。

Redis的核心是所有数据都存在内存里，单线程去操作就是效率最高的。因为多线程的本质就是（单个）CPU模拟出来多个线程的情况，代价就是上下文切换（耗时的操作！）。对于一个内存的系统而言，没有上下文切换的话效率就是最高的。

Redis用单个CPU绑定一块内存的数据，然后针对这块内存的数据进行多次读写，都是在一个CPU上完成，它是单线程处理这个事。在内存的情况下而言，这个就是最佳方案。

#### Redis数据类型

##### String

```bash
# move key db ---> 将当前db的某个key移动到另一个db

# expire key 秒钟：为给定 key 设置生存时间，当 key 过期时(生存时间为 0 )，它会被自动删 除。
# ttl key 查看还有多少秒过期，-1 表示永不过期，-2 表示已过期



# =================================================== 
# incr、decr 一定要是数字才能进行加减，+1 和 -1。 
# incrby、decrby 命令将 key 中储存的数字加上指定的增量值。 # ===================================================
127.0.0.1:6379> set views 0          # 设置浏览量为0 
OK
127.0.0.1:6379> incr views           # 浏览 + 1
(integer) 1 
127.0.0.1:6379> incr views           # 浏览 + 1
(integer) 2
127.0.0.1:6379> decr views           # 浏览 - 1 
(integer) 1
127.0.0.1:6379> incrby views 10       # +10
 (integer) 11 
 127.0.0.1:6379> decrby views 10       # -10
  (integer) 1
  

# =================================================== 
# setex（set with expire）键秒值 
# setnx（set if not exist） 
# =================================================== 
127.0.0.1:6379> setex key3 60 expire # 设置过期时间 
OK
127.0.0.1:6379> ttl key3 # 查看剩余的时间 
(integer) 55 
127.0.0.1:6379> setnx mykey "redis" # 如果不存在就设置，成功返回1 
(integer) 1 
127.0.0.1:6379> setnx mykey "mongodb" # 如果存在就设置，失败返回0 
(integer) 0 
127.0.0.1:6379> get mykey 
"redis"

# =================================================== 
# 如果所有给定 key 都设置失败(至少有一个 key 已经存在)，那么返回 0 。这是原子操作！
# ===================================================

# 传统对象缓存 
set user:1 value(json数据) 
# 可以用来缓存对象 
mset user:1:name zhangsan user:1:age 2 
mget user:1:name user:1:age 

```
常规key-value缓存应用：
常规计数：微博数，粉丝数等。

浏览量的操作，某篇文章 
set view:article:UUID-sdsd-5878 0
incr view:article:UUID-sdsd-5878 # 浏览量+1

##### List

List的所有命令都是以L开头（假的，还有R开头的，嘻嘻）

常用命令：lpush（添加左边元素）,rpush,lpop（移除左边第一个元素）,rpop,lrange（获取列表片段，LRANGE key start stop）

List就是一个**双向链表**！

**key是List的名字！！**

```bash
# =================================================== 
# Lpush：将一个或多个值插入到列表头部。（左） 
# rpush：将一个或多个值插入到列表尾部。（右） 
# lrange：返回列表中指定区间内的元素，区间以偏移量 START 和 END 指定。 
# 其中 0 表示列表的第一个元素， 1 表示列表的第二个元素，以此类推。 
# 你也可以使用负数下标，以 -1 表示列表的最后一个元素， -2 表示列表的倒数第二个元素，以此 
类推。 
# =================================================== 
127.0.0.1:6379> LPUSH list "one" 
(integer) 1 
127.0.0.1:6379> LPUSH list "two" 
(integer) 2 
127.0.0.1:6379> RPUSH list "right" 
(integer) 3 
127.0.0.1:6379> Lrange list 0 -1 
1) "two" 
2) "one" 
3) "right" 
127.0.0.1:6379> Lrange list 0 1 
1) "two" 
2) "one" 

# =================================================== 
# lpop 命令用于移除并返回列表的第一个元素。当列表 key 不存在时，返回 nil 。 
# rpop 移除列表的最后一个元素，返回值为移除的元素。 
# =================================================== 

# Lindex，按照索引下标获得元素（-1代表最后一个，0代表是第一个）

# =================================================== 
# lrem key count value 从key（List的名字）中删掉count个 值为value 的元素
# =================================================== 
127.0.0.1:6379> lrem list 1 "two" 
(integer) 1 
127.0.0.1:6379> Lrange list 0 -1 
1) "three" 
2) "one" 

# lset key index value 更新操作，要是index没有值的话就报错（因为是更新嘛，旧的都没有怎么更新）

# linsert key before/after pivot value 用于在列表的元素前或者后插入元素。 
# 将值 value 插入到列表 key 当中，位于值 pivot 之前或之后。 （pivot是List中目前存在的值！）
```
- 它是一个字符串链表，left，right 都可以插入添加
- 如果键不存在，创建新的链表
- 如果键已存在，新增内容
- 如果值全移除，对应的键也就消失了
- 链表的操作无论是头和尾效率都极高，但假如是对中间元素进行操作，效率就很惨淡了。

应用：**最新消息排行**(比如朋友圈的时间线)

List的另一个应用就是**消息队列**，可以利用List的PUSH操作，将任务存在List中，然后工作线程再用POP操作将任务取出进行执行。

##### Set
所有命令都是以S开头的

常用命令：sadd,spop,smembers,sunion 

**scard**，获取集合里面的元素个数

```bash
# =================================================== 
# sadd 将一个或多个成员元素加入到集合中，不能重复 
# smembers 返回集合中的所有的成员。 
# sismember 命令判断成员元素是否是集合的成员。 
# ===================================================

# scard，获取集合里面的元素个数

# srandmember key 命令用于返回集合中的一个随机元素。

# =================================================== 
- 数字集合类 (重要)
- 差集： sdiff 
- 交集： sinter
- 并集： sunion 
# ===================================================
127.0.0.1:6379> sadd key1 "a" 
(integer) 1 
127.0.0.1:6379> sadd key1 "b" 
(integer) 1 
127.0.0.1:6379> sadd key1 "c" 
(integer) 1 
127.0.0.1:6379> sadd key2 "c" 
(integer) 1 
127.0.0.1:6379> sadd key2 "d" 
(integer) 1 
127.0.0.1:6379> sadd key2 "e" 
(integer) 1 
127.0.0.1:6379> SDIFF key1 key2 # 差集 以key1为基准，key1中没有的！！！！
1) "a" 
2) "b" 
127.0.0.1:6379> SINTER key1 key2 # 交集 
1) "c" 
127.0.0.1:6379> SUNION key1 key2 # 并集 
1) "a" 
2) "b" 
3) "c" 
4) "e" 
5) "d"
```

应用：在微博应用中，可以将一个用户所有的关注人存在一个集合中，将其所有粉丝存在一个集合。

Redis还为集合提供了求交集、并集、差集等操作，可以非常方便的实现如共同关注、共同喜好、二度好友等功能，对上面的所有集合操作，你还可以使用不同的命令选择将结果返回给客户端还是存集到一个新的集合中。

##### Hash

所有命令都是H开头，里面都装的是**键值对**！

感觉跟String差不多，相对于更方便一些

String: 
mset user:1:name zhangsan user:1:age 2 

Hash: 
hmset user:1 name zhangsan age 2
hgetall user:1

```bash
# =================================================== 
# hset、hget 命令用于为哈希表中的字段赋值 。 
# hmset、hmget 同时将多个field-value对设置到哈希表中。会覆盖哈希表中已存在的字段。 
# hgetall 用于返回哈希表中，所有的字段和值。 
# hdel 用于删除哈希表 key 中的一个或多个指定字段 
# =================================================== 
127.0.0.1:6379> hset myhash field1 "kuangshen" 
(integer) 1 
127.0.0.1:6379> hget myhash field1 
"kuangshen" 
127.0.0.1:6379> HMSET myhash field1 "Hello" field2 "World" 
OK
127.0.0.1:6379> HGET myhash field1 
"Hello" 
127.0.0.1:6379> HGET myhash field2 
"World" 
127.0.0.1:6379> hgetall myhash 
1) "field1" 
2) "Hello" 
3) "field2"
4) "World" 

# =================================================== 
# hkeys 获取哈希表中的所有域（field）。 
# hvals 返回哈希表所有域(field)的值。 
# =================================================== 
127.0.0.1:6379> HKEYS myhash 
1) "field2" 
2) "field1" 
127.0.0.1:6379> HVALS myhash 
1) "World" 
2) "Hello" 
```
Hash是一个String类型的field和value的映射表，Hash特别适合用于**存储对象**。

存储部分变更的数据，如用户信息等。

##### Zset
命令都是Z开头的

常用：zadd,zrange,zrem,zcard

在set基础上，加一个score值。之前set是k1 v1 v2 v3，现在zset是 k1 score1 v1 score2 v2

**Zcard** 命令用于计算集合中元素的数量。 Set的是Scard
```bash
# zadd 将一个或多个成员元素及其分数值加入到有序集当中。
127.0.0.1:6379> zadd myset 1 "one" 
(integer) 1 
127.0.0.1:6379> zadd myset 2 "two" 3 "three" 
(integer) 2 

# zrange 返回有序集中，指定区间内的成员 
127.0.0.1:6379> ZRANGE myset 0 -1 
1) "one" 
2) "two" 
3) "three"

# zrangebyscore 返回有序集合中指定分数区间的成员列表。有序集成员按分数值递增(从小到大) 
次序排列。 
127.0.0.1:6379> zadd salary 2500 xiaoming 
(integer) 1 
127.0.0.1:6379> zadd salary 5000 xiaohong 
(integer) 1 
127.0.0.1:6379> zadd salary 500 xiaohuang
(integer) 1
127.0.0.1:6379> ZRANGEBYSCORE salary -inf +inf # 显示整个有序集 
1) "xiaohuang" 
2) "xiaoming" 
3) "xiaohong" 
127.0.0.1:6379> ZRANGEBYSCORE salary -inf +inf withscores # 递增排列 
1) "xiaohuang" 
2) "500" 
3) "xiaoming" 
4) "2500" 
5) "xiaohong" 
6) "5000" 

# ZREVRANGE 递减（Z reverse range）
127.0.0.1:6379> ZREVRANGE salary 0 -1 WITHSCORES # 递减排列 
1) "xiaohong" 
2) "5000" 
3) "xiaoming" 
4) "2500" 
5) "xiaohuang" 
6) "500" 
127.0.0.1:6379> ZRANGEBYSCORE salary -inf 2500 WITHSCORES # 显示工资 <=2500 
的所有成员 
1) "xiaohuang" 
2) "500" 
3) "xiaoming" 
4) "2500" 

``` 

和set相比，sorted set增加了一个权重参数score，使得集合中的元素能够按score进行有序排列，比如一个存储全班同学成绩的sorted set，其集合value可以是同学的学号，而score就可以是其考试得分，这样在数据插入集合的时候，就已经进行了天然的排序。

可以用sorted set来做带权重的队列，比如普通消息的score为1，重要消息的score为2，然后工作线程可以选择按score的倒序来获取工作任务。让重要的任务优先执行。

排行榜应用，取TOP N操作 ！

##### GEO

**基于Zset**！！！！！

常用命令：geoadd、geopos、geodist、georadius、
georadiusbymember、gethash

```bash
# geoadd key longitude latitude member ... 
# 必须先输入经度,然后再输入纬度


# geopos key member [member...] 
#从key里返回所有给定位置元素的位置（经度和纬度） 


# geodist key member1 member2 [unit] 
# 返回两个给定位置之间的距离，如果两个位置之间的其中一个不存在,那么命令返回空值。 
# 指定单位的参数unit必须是以下单位的其中一个： 
# m表示单位为米 
# km表示单位为千米 
# mi表示单位为英里 
# ft表示单位为英尺 
# 如果用户没有显式地指定单位参数,那么geodist默认使用米作为单位。 

# georadius key longitude latitude radius m|km|ft|mi [withcoord][withdist][withhash][asc|desc][count count] 
# 以给定的 经纬度 为中心， 找出某一半径内的元素 


# georadiusbymember key member radius m|km|ft|mi [withcoord][withdist][withhash][asc|desc][count count] 
# 找出位于指定范围内的元素，中心点是由给定的位置元素决定 


# geohash key member [member...] 
# Redis使用geohash将二维经纬度转换为一维字符串，字符串越长表示位置更精确,两个字符串越相似表示距离越近。

```

GEO没有提供删除成员的命令，但是因**为GEO的底层实现是zset**，所以可以借用zrem命令实现对地理位置信息的删除

```bash
127.0.0.1:6379> geoadd china:city 116.23 40.22 beijing
1

127.0.0.1:6379> zrange china:city 0 -1 # 查看全部的元素 
重庆
西安
深圳
武汉
杭州
上海
beijing
北京

127.0.0.1:6379> zrem china:city beijing # 移除元素 
1 

127.0.0.1:6379> zrem china:city 北京 # 移除元素 
1

127.0.0.1:6379> zrange china:city 0 -1 
重庆
西安
深圳
武汉
杭州
上海
```
##### HyperLogLog
Redis HyperLogLog 是用来做基数统计的算法

命令都是以PF开头的！

基数：统计一个集合中**不重复**的元素个数！！！

比如数据集 {1, 3, 5, 7, 5, 7, 8}， 那么这个数据集的基数集为 {1, 3, 5 ,7, 8}, 基数(不重复元素)为5。

HyperLogLog则是一种算法，它提供了**不精确**的去重计数方案。

假如我要统计网页的UV（浏览用户数量，一天内同一个用户多次访问只能算一次），传统的解决方案是使用Set来保存用户id，然后统计Set中的元素数量来获取页面UV。但这种方案只能承载少量用户，一旦用户数量大起来就需要消耗大量的空间来存储用户id。

我的**目的是统计用户数量而不是保存用户**，这简直是个吃力不讨好的方案！

而使用Redis的HyperLogLog最多需要12k就可以统计大量的用户数，尽管它大概有0.81%的错误率，但对于统计UV这种不需要很精确的数据是可以忽略不计的。

常用命令：PFadd，PFcount，PFmerge

| 命令                                       | 描述                                                |
| ------------------------------------------ | --------------------------------------------------- |
| [PFADD key element [element ...]           | 添加指定元素到 HyperLogLog 中。                     |
| [PFCOUNT key [key ...]                     | 返回给定 HyperLogLog 的基数估算值                   |
| [PFMERGE destkey sourcekey[sourcekey ...] | 将多个 HyperLogLog 合并为一个 HyperLogLog，并集计算 |

```bash
127.0.0.1:6379> PFADD mykey a b c d e f g h i j 
1
127.0.0.1:6379> PFCOUNT mykey 
10
127.0.0.1:6379> PFADD mykey2 i j z x c v b n m 
1
127.0.0.1:6379> PFMERGE mykey3 mykey mykey2 
OK
127.0.0.1:6379> PFCOUNT mykey3 
15 
```

##### BitMap
只有两个状态的，都可以用BitMap（如登录、未登录；打卡、未打卡；活跃、不活跃；..）

在开发中，可能会遇到这种情况：需要统计用户的某些信息，如活跃或不活跃，登录或者不登录；又如需要记录用户一年的打卡情况，打卡了是1， 没有打卡是0，如果使用普通的key/value存储，则要记录365条记录，如果用户量很大，需要的空间也会很大。

所以 Redis 提供了 Bitmap 位图这中数据结构，Bitmap 就是通过操作二进制位来进行记录，即为 0 和 1；如果要记录 365 天的打卡情况，使用 Bitmap表示的形式大概如下：101000111000111...........................。

这样有什么好处呢？当然就是节约内存了，365 天相当于 365 bit，又 1 字节 = 8 bit , 所以相当于使用 46 个字节即可。

| 命令                      | 描述                                  |
| ------------------------- | ------------------------------------- |
| SETBIT key offset value   | 设置 key 的第 offset 位为value (1或0) |
| GETBIT key offset         | 获取offset设置的值，未设置过默认返回0 |
| bitcount key [start, end] |  统计 key 上位为1的个数 |

#### Redis事务
Redis 事务的本质是一组命令的集合

multi
...
exec

Redis事务没有隔离级别的概念：
- 批量操作在发送 EXEC 命令前被放入队列缓存，并不会被实际执行！

Redis不保证原子性：
- Redis中，单条命令是原子性执行的，但事务不保证原子性，且没有回滚。事务中任意命令执行失败（**指的是命令性错误**！！），其余的命令仍会被执行。如果是**语法性错误，则全部失败**！！！

1. 命令性错误（类似于java编译性错误），则执行EXEC命令时，所有命令都不会
执行
 ![命令性错误](https://raw.githubusercontent.com/koshunho/koshunhopic/master/blog1595246376903.png)
 
2. 语法性错误（类似于java的1/0的运行时异常），则执行EXEC命令时，其他正确命令会被执行，错误命令抛出异常。
 
 ![语法性错误](https://raw.githubusercontent.com/koshunho/koshunhopic/master/blog1595246440326.png)
 
 ##### Watch监控
 
 Watch其实就是乐观锁。watch在监控某一个key，如果exec时，这个key的值还是原来一致，就执行事务；不一致，则所有命令都不执行
 
 1. 初始化信用卡可用余额和欠额
```bash
127.0.0.1:6379> set balance 100 
OK
127.0.0.1:6379> set debt 0 
OK
```
2. 使用watch检测balance，事务期间balance数据**未变动**，事务执行成功
```bash
127.0.0.1:6379> watch balance 
OK
127.0.0.1:6379> MULTI 
OK
127.0.0.1:6379> decrby balance 20 
QUEUED 
127.0.0.1:6379> incrby debt 20 
QUEUED 
127.0.0.1:6379> exec 
1) (integer) 80 
2) (integer) 20 
```

3. 使用watch检测balance，事务期间balance数据**变动**，事务执行失败！
```bash
# 窗口一 
127.0.0.1:6379> watch balance 
OK
127.0.0.1:6379> MULTI # 执行完毕后，
OK
127.0.0.1:6379> decrby balance 20 
QUEUED 
127.0.0.1:6379> incrby debt 20 # 先不执行exec!!!现在跳到窗口二代码测试 
QUEUED 
127.0.0.1:6379> exec # 修改失败！ 
(nil) 


# 窗口二 
127.0.0.1:6379> get balance 
"80" 
127.0.0.1:6379> set balance 200 # 返回窗口一exec，提示修改失败！
OK 

-----------
# 窗口一：出现问题后放弃监视，然后重来！ 
127.0.0.1:6379> UNWATCH # 放弃监视 
OK
127.0.0.1:6379> watch balance 
OK
127.0.0.1:6379> MULTI 
OK
127.0.0.1:6379> decrby balance 20 
QUEUED 
127.0.0.1:6379> incrby debt 20
QUEUED 
127.0.0.1:6379> exec # 成功！ 
1) (integer) 180 
2) (integer) 40 

```

出现问题后放弃监视，然后重来！

一旦执行 EXEC 开启事务的执行后，**无论事务使用执行成功， WATCH 对变量的监控都将被取消**。

故当事务执行失败后，需**重新执行WATCH命令**对变量进行监控，并开启新的事务进行操作。

watch指令类似于乐观锁，在事务提交时，如果watch监控的多个KEY中任何KEY的值已经被其他客户端更改，则使用EXEC执行事务时，事务队列将不会被执行，同时返回Nullmulti-bulk应答以通知调用者事务执行失败。

#### RDB(Redis DataBase)

非常重要，背都要背下来！

在redis.conf中 查看 SNAPSHOTTING 快照
```bash
# 900秒（15分钟）内至少1个key值改变（则进行数据库保存--持久化） 
save 900 1 

# 300秒（5分钟）内至少10个key值改变（则进行数据库保存--持久化） 
save 300 10 

# 60秒（1分钟）内至少10000个key值改变（则进行数据库保存--持久化） 
save 60 10000 

stop-writes-on-bgsave-error yes # 持久化出现错误后，是否依然进行继续进行工作

rdbcompression yes # 使用压缩rdb文件 yes：压缩，但是需要一些cpu的消耗。no：不压 缩，需要更多的磁盘空间 

rdbchecksum yes # 是否校验rdb文件，更有利于文件的容错性，但是在保存rdb文件的时 
候，会有大概10%的性能损耗 

dbfilename dump.rdb # dbfilenamerdb文件名称

dir ./ # dir 数据目录，数据库的写入会在这个目录。rdb、aof文件也会写在这个目录

```

可以看到，RBD 保存的是 dump.rdb 文件。RDB 是整合内存的**压缩过**的Snapshot，RDB 的数据结构，可以配置复合的快照触发条件。

若要修改完毕需要立马生效，可以**手动使用 save 命令**！立马生效！

![save](https://raw.githubusercontent.com/koshunho/koshunhopic/master/blog1595262546186.png)

save和bgsave的区别：
- save 时**只管保存，其他不管，全部阻塞**！

- bgsave，Redis 会在**后台异步进行快照操作**，快照**同时还可以响应客户端请求**。可以通过lastsave命令获取最后一次成功执行快照的时间。

如果想禁用RDB持久化的策略，只要不设置任何save指令，或者给save传入一个空字符串参数也可以。

```bash
# 用#全部注释掉所有save策略！
#save 900 1 
 
#save 300 10 

# save 60 10000 
```

在指定的时间间隔内（也就是redis.conf文件中的 SNAPSHOTTING中的save操作）将内存中的**数据集快照**写入磁盘，也就是行话讲的Snapshot快照，它恢复时是将快照文件直接读到内存里。


Redis会单独创建（fork）一个子进程来进行持久化，会先将数据写入到一个临时文件中，待持久化过程都结束了，再用这个临时文件替换上次持久化好的文件。整个过程中，主进程是不进行任何IO操作的。这就确保了极高的性能。如果需要进行大规模数据的恢复，且对于数据恢复的完整性不是非常敏感，那RDB方式要比AOF方式更加的高效。RDB的缺点是最后一次持久化后的数据可能丢失。


Redis会单独fork一个子进程进行持久化，会先把数据写入一个临时文件中，待持久化过程都结束了，就用这个临时文件替换上次持久化好的文件。整个过程中，主进程是不进行任何IO操作的，这就确保了极高的性能。如果需要进行大规模数据的回复，对于数据恢复的完整性不是很敏感，那么RDB要比AOP更加高效。缺点是最后一次持久化的数据可能会丢失！

如何触发RDB快照？
1. 配置文件中默认的快照配置，建议多用一台机子作为备份，复制一份 dump.rdb
2. 命令save或者是bgsave
- save 时**只管保存，其他不管，全部阻塞**
- bgsave，Redis 会在**后台异步进行快照操作**，快照同时**还可以响应客户端请求**。可以通过lastsave命令获取最后一次成功执行快照的时间。
3. 执行flushall命令，也会产生 dump.rdb 文件，但里面是空的，无意义 !
4. 退出的时候也会产生 dump.rdb 文件！

如何恢复？
- 将备份文件（dump.rdb）移动到redis安装目录并启动服务即可

优点：
1. 适合大规模的数据恢复（因为RBD文件是压缩过的snapshot快照文件）
2. 对数据完整性和一致性要求不高

缺点：
1. 在**一定间隔时间做一次备份**，所以如果Redis意外down掉的话，就会丢失最后一次快照后的所有修改
2. Fork的时候，**内存中的数据被克隆了一份**，大致**2倍的膨胀性**需要考虑。

#### AOF（Append Only File）
以**日志**的形式来记录每个**写**操作，**将Redis执行过的所有 写 指令记录下来**（读操作不记录），**只许追加文件**但不可以改写文件，Redis启动之初会读取该文件重新构建数据，换言之，redis重启的话就根据日志文件的内容将写指令从前到后执行一次以完成数据的恢复工作。

Aof保存的是 appendonly.aof 文件。

注意，可以同时和RDB一起使用！而且会优先恢复AOF文件！

在redis.conf中
```bash
appendonly no # 是否以append only模式作为持久化方式，默认使用的是rdb方式持久化，这种方式在许多应用中已经足够用了 

appendfilename "appendonly.aof" # appendfilename AOF 文件名称 

appendfsync everysec # appendfsync aof持久化策略的配置 
# no表示不执行fsync，由操作系统保证数据同步到磁盘，速度最快。 
# always表示每次写入都执行fsync，以保证数据同步到磁盘。 
# everysec表示每秒执行一次fsync，可能会导致丢失这1s数据。 

No-appendfsync-on-rewrite no #重写时是否可以运用Appendfsync，用默认no即可，保证数据安全性

Auto-aof-rewrite-min-size 64mb # 设置重写的基准值 默认为64MB
 
```

正常恢复：
- 启动：设置Yes，修改默认的appendonly no，改为yes
- 将有数据的AOF文件复制一份保存到对应目录
- 恢复：重启Redis然后重新加载

异常恢复：
- 启动：设置Yes
- 故意破坏 appendonly.aof 文件！
- 修复： 执行`redis-check-aof --fix appendonly.aof `命令 进行修复
- 恢复：重启 redis 然后重新加载


执行`redis-check-aof --fix appendonly.aof `命令时，会把错误的数据清除！！！所以会丢失部分数据（总比全部丢失好）

Rewrite?

AOF 采用文件追加方式，文件会越来越大，为避免出现此种情况，新增了重写机制，当AOF文件的大小超过所设定的阈值时，Redis 就会启动AOF 文件的内容压缩，**只保留可以恢复数据的最小指令集**！

AOF重写可以由用户通过调用`BGrewriteof`手动触发！

```bash
 # 假设服务器对键list执行了以下命令s;
127.0.0.1:6379> RPUSH list "A" "B"
(integer) 2
127.0.0.1:6379> RPUSH list "C"
(integer) 3
127.0.0.1:6379> RPUSH list "D" "E"
(integer) 5
127.0.0.1:6379> LPOP list
"A"
127.0.0.1:6379> LPOP list
"B"
127.0.0.1:6379> RPUSH list "F" "G"
(integer) 5
127.0.0.1:6379> LRANGE list 0 -1
1) "C"
2) "D"
3) "E"
4) "F"
5) "G"
127.0.0.1:6379> 
```
当前列表键list在数据库中的值就为["C", "D", "E", "F", "G"]。要使用尽量少的命令来记录list键的状态，最简单的方式不是**去读取和分析现有AOF文件的内容**，而是**直接读取list键在数据库中的当前值**，然后用一条RPUSH list "C" "D" "E" "F" "G"代替前面的6条命令。

原理：AOF 文件持续增长而过大时，会fork出一条新进程来将文件重写（也是先写临时文件最后再rename），遍历新进程的内存中数据，每条记录有一条的Set语句。**重写aof文件的操作，并没有读取旧的aof文件，这点和快照有点类似**！


触发条件？
每次当serverCron（服务器周期性操作函数）函数执行时，它会检查以下条件是否全部满足，如果全部满足的话，就触发自动的AOF重写操作：

- 没有BGSAVE命令（RDB持久化）/AOF持久化在执行；
- 没有BGREWRITEAOF在进行；
- 当前AOF文件大小要大于server.aof_rewrite_min_size（默认为1MB），或者在redis.conf配置了auto-aof-rewrite-min-size大小（默认为64MB）；
- **当前写入日志文件的大小**超过**上一次rewrite之后的文件大小**之间的比率等于指定的增长百分比（在配置文件设置了auto-aof-rewrite-percentage参数，不设置默认为100%）
  
如果前面三个条件都满足，并且当前AOF文件大小比最后一次AOF重写时的大小要大于指定的百分比，那么触发自动AOF重写。

简而言之：
- rewrite机制：aof里存放了所有的redis 操作指令，当aof文件达到一定条件或者手动bgrewriteaof命令都可以触发rewrite。

- rewrite之后aof文件会保存keys的最后的状态，清除掉之前冗余的，来缩小这个文件。
- 自动触发的条件：
  long long growth =(server.appendonly_current_size*100/base) - 100;
  
  if (growth >=server.auto_aofrewrite_perc)
  
  在配置文件里设置过：auto-aof-rewrite-percentage 100 （**当前写入日志文件的大小**超过**上一次rewrite之后的文件大小**的百分之100时就是2倍时触发Rewrite）
  
优点：
1. 每修改同步：appendfsync always 同步持久化，每次发生数据变更会被立即记录到磁盘，性能较差
但数据完整性比较好
2. 每秒同步： appendfsync everysec 异步操作，每秒记录 ，如果一秒内宕机，有数据丢失
3. 不同步： appendfsync no 从不同步

缺点：
1. 相同数据集的数据而言，aof 文件要**远大于** rdb文件，恢复速度慢于 rdb。 2.
2. AOF 运行效率要慢于 RDB，每秒同步策略效率较好，不同步效率和rdb相同。


#### 持久化总结
1. 只做缓存，如果你只希望你的数据在服务器运行的时候存在，你也可以不使用任何持久化
2. 同时开启两种持久化方式 
- 在这种情况下，当redis重启的时候会**优先载入AOF文件**来恢复原始的数据，因为在通常情况下AOF文件保存的数据集要比RDB文件保存的数据集要完整。 
- RDB 的数据不实时，同时使用两者时服务器重启也只会找AOF文件，那要不要只使用AOF呢？建议不要，因为RDB更适合用于备份数据库（AOF在不断变化不好备份），快速重启，而且不会有 
AOF可能潜在的Bug，留着作为一个万一的手段。 
3. 性能建议 
- 因为RDB文件只用作后备用途，建议只在Slave上持久化RDB文件，而且只要15分钟备份一次就够了，只保留 save 900 1 这条规则。 
- 如果Enable AOF ，好处是在最恶劣情况下也只会丢失不超过两秒数据，启动脚本较简单只load自己的AOF文件就可以了，代价一是带来了持续的IO，二是AOF rewrite 的最后将 rewrite 过程中产生的新数据写到新文件造成的阻塞几乎是不可避免的。只要硬盘许可，应该尽量减少AOF rewrite的频率，AOF重写的基础大小默认值64M太小了，可以设到5G以上，默认超过原大小100%大小重写可以改到适当的数值。 
- 如果不Enable AOF ，仅靠 Master-Slave Repllcation 实现高可用性也可以，能省掉一大笔IO，也减少了rewrite时带来的系统波动。代价是如果Master/Slave 同时倒掉，会丢失十几分钟的数据，启动脚本也要比较两个 Master/Slave 中的 RDB文件，载入较新的那个，微博就是这种架构。 

#### 主从复制

主节点负责写，从节点负责读（也只能读！）

![主从复制](https://raw.githubusercontent.com/koshunho/koshunhopic/master/blog1595328742951.png)

作用：
1. 数据冗余：主从复制实现了数据的热备份，是持久化之外的一种数据冗余方式。
2. 故障恢复：当主节点出现问题时，可以由从节点提供服务，实现快速的故障恢复；实际上是一种服务
的冗余。
3. 负载均衡：在主从复制的基础上，配合读写分离，可以由主节点提供写服务，由从节点提供读服务（即写Redis数据时应用连接主节点，读Redis数据时应用连接从节点），分担服务器负载；尤其是在写少读多的场景下，通过多个从节点分担读负载，可以大大提高Redis服务器的并发量。
4. 高可用基石：除了上述作用以外，主从复制还是哨兵和集群能够实施的基础，因此说主从复制是Redis高可用的基础。

启动服务：
1. reids启动，cmd进入对应的redis文件夹下面-> redis-server.exe redis.windows6380.conf（6379/6381同，不再赘述）
2. 哨兵sentinel.conf启动：编写一个bat来启动sentinel，在每个节点目录下建立startup_sentinel.bat，内容如下：
```bash
title sentinel-6380
redis-server.exe sentinel.conf --sentinel
```
需要注意的是，每个配置文件port端口是唯一性的。
 
启动完成后，对应的窗口不要关闭，并且需要检查一下是否启动成功。每个redis都要2个窗口，一个是服务启动，一个是哨兵


启动客户端：
1. Redis: cmd进入对应的redis文件夹-> redis-cli.exe -p 6380，输入info replication
2. Sentinel: cmd进入对应的redis文件夹-> redis-cli.exe -p 哨兵配置端口，输入info sentinel

##### 配置
配从库不配主库

基本配置：
1. 在redis.conf里面配置主节点。这种操作是永久的
```bash
# 主机ip和port
# slaveof <masterip> <masterport>  

# 主机如果有密码就写
# masterauth <master-password>
```

or

2. 在客户端里面配置，但是这是临时的。每次与 master 断开之后，都需要重新连接
```bash
slaveof 主库ip 主库端口 # 配置主从 
Info replication # 查看信息
```

##### 一主二从

在主机设置值，在从机都可以取到！从机不能写值！

![一主二从](https://raw.githubusercontent.com/koshunho/koshunhopic/master/blog1595330375998.png)

##### 配置原理
Slave 启动成功连接到 master 后会发送一个sync命令

Master 接到命令，启动后台的存盘进程，同时收集所有接收到的用于修改数据集命令，在后台进程执行

完毕之后，master将传送整个数据文件到slave，并完成一次完全同步。

==全量复制==：而slave服务在接收到数据库文件数据后，将其存盘并加载到内存中。

==增量复制==：Master 继续将新的所有收集到的修改命令依次传给slave，完成同步

但是**只要是重新连接master，一次完全同步（全量复制）将被自动执行**

##### 谋朝篡位
一主二从的情况下，如果主机断了，从机可以使用命令` SLAVEOF NO ONE `将自己改为主机！这个时候其余的从机连接到这个节点。（在没有哨兵模式之前都是这样重新选老大的！）

对一个从属服务器执行命令 `SLAVEOF NO ONE` 将使得这个从属服务器关闭复制功能，并从从属服务器转变回主服务器，**原来同步所得的数据集不会被丢弃**。

`SLAVEOF NO ONE`
`SLAVEOF NO ONE`
`SLAVEOF NO ONE`
`SLAVEOF NO ONE`
`SLAVEOF NO ONE`
`SLAVEOF NO ONE`
`SLAVEOF NO ONE`
**原来同步所得的数据集不会被丢弃**
**原来同步所得的数据集不会被丢弃**
**原来同步所得的数据集不会被丢弃**
**原来同步所得的数据集不会被丢弃**
**原来同步所得的数据集不会被丢弃**
**原来同步所得的数据集不会被丢弃**

##### 哨兵模式 
自动版谋朝篡位！

主从切换技术的方法是：当主服务器宕机后，需要手动把一台从服务器切换为主服务器，这就需要人工干预，费事费力，还会造成一段时间内服务不可用。

其原理是哨兵通过发送命令，等待Redis服务器响应，从而监控运行的多个Redis实例。

一般多哨兵，哨兵间也互相监控！

由于windows版Redis没有sentinel.conf文件，所以自己在Redis文件夹中建一个

记住一个单词：failover（故障转移）

```bash
# 当前Sentinel服务运行的端口
port 26379 

#去监视一个名为mymaster的主redis实例，这个主实例的IP地址为本机地址127.0.0.1，端口号为6379，而将这个主实例判断为失效至少需要2个 Sentinel进程的同意，只要同意Sentinel的数量不达标，自动failover就不会执行
sentinel monitor mymaster 127.0.0.1 6379 2:Sentinel 

#指定了Sentinel认为Redis实例已经失效所需的毫秒数。当 实例超过该时间没有返回PING，或者直接返回错误，那么Sentinel将这个实例标记为主观下线。
#只有一个 Sentinel进程将实例标记为主观下线并不一定会引起实例的自动故障迁移：只有在足够数量的Sentinel都将一个实例标记为主观下线之后，实例才会被标记为客观下线，这时自动故障迁移才会执行
sentinel down-after-milliseconds mymaster 5000 

#指定了在执行故障转移时，最多可以有多少个从Redis实例在同步新的主实例，在从Redis实例较多的情况下这个数字越小，同步的时间越长，完成故障转移所需的时间就越长
sentinel parallel-syncs mymaster

# 如果在该时间（ms）内未能完成failover操作，则认为该failover失败
sentinel failover-timeout mymaster 15000
```

查看sentinel状态 `info sentinel`
`info sentinel`
`info sentinel`
`info sentinel`
`info sentinel`
`info sentinel`
`info sentinel`

如果此时主机shutdown，过一会儿就会从 从机 中 随机选择一个做主机（里面有一个投票算法）

优点：
1. 哨兵集群模式是基于主从模式的，所有主从的优点，哨兵模式同样具有。
2. 主从可以切换，故障可以转移，系统可用性更好。
3. 哨兵模式是主从模式的升级，系统更健壮，可用性更高。

缺点：
1. Redis较难支持在线扩容，在集群容量达到上限时在线扩容会变得很复杂。
2. 实现哨兵模式的配置也不简单，甚至可以说有些繁琐

#### 缓存穿透

简单来说就是 **查不到**

缓存穿透的概念很简单，用户想要查询一个数据，发现redis内存数据库没有，也就是缓存没有命中，于是向持久层数据库查询，发现也没有，于是本次查询失败。当用户很多的时候，缓存都没有命中，于是都去请求了持久层数据库。这会给持久层数据库造成很大的压力，这时候就相当于出现了缓存穿透。

解决方案：
1. 布隆过滤器
 ![布隆过滤器](https://raw.githubusercontent.com/koshunho/koshunhopic/master/blog1595343255687.png)
 
 布隆过滤器可以告诉你**某样东西一定不存在或者可能存在**。
 
 布隆过滤器（Bloom Filter）本质上是由长度为 m 的位向量或位列表（仅包含 0 或 1 位值的列表）组成，最初所有的值均设置为 0，如下图所示。
 ![](https://raw.githubusercontent.com/koshunho/koshunhopic/master/blog1595343720860.png)
 
 为了将数据项添加到布隆过滤器中，我们会**提供 K 个不同**的==哈希函数==，并将结果位置上对应位的值置为 “1”。
 ![](https://raw.githubusercontent.com/koshunho/koshunhopic/master/blog1595343757193.png)
 
 2. 缓存空对象
 ![空对象](https://raw.githubusercontent.com/koshunho/koshunhopic/master/blog1595343284187.png)
 
但是这种方法会存在两个问题：
1、如果空值能够被缓存起来，这就意味着缓存需要更多的空间存储更多的键，因为这当中可能会有很多的空值的键；

2、即使对空值设置了过期时间，还是会存在缓存层和存储层的数据会有一段时间窗口的不一致，这对于需要保持一致性的业务会有影响。

##### 布隆过滤器预热数据
有时一些特殊的任务需要在系统启动时执行，例如配置文件加载、数据库初始化等操作。SpringBoot提供了一个接口 `ApplicationRunner`重写run()
`ApplicationRunner`
`ApplicationRunner`
`ApplicationRunner`
`ApplicationRunner`
`ApplicationRunner`
`ApplicationRunner`

比如启动时从数据库先加载所有的ID。
```java
@Component
public class BloomFilterInit implements ApplicationRunner {
    private BloomFilter bloomFilter;

    @Autowired
    private EmployeeMapper employeeMapper;
	
    @Override
    public void run(ApplicationArguments args) throws Exception {
        List<Integer> list = employeeMapper.getAllEmployeesId();
        if(list.size()>0){
            bloomFilter=bloomFilter.create(Funnels.integerFunnel(),list.size(),0.01);
            for(int i = 0; i < list.size(); i++){
                bloomFilter.put(list.get(i));
            }
            System.out.println("预热employeesId到布隆过滤器成功！");
        }
    }
	
	public BloomFilter getIntegerBloomFilter(){
        return bloomFilter;
    }
}
```

查找一个key是否存在布隆过滤器 `mightCount(T object)`方法
```java
        if(!bloomFilterInit.getIntegerBloomFilter().mightContain(id)){
            System.out.println("该EmployeeId在布隆过滤器中不存在！");
            return null;
        }
```

添加一个key进入布隆过滤器 `put(T object)`方法
```java
bloomFilterInit.getIntegerBloomFilter().put(employee.getId());	
```
#### 缓存击穿

**查太多**！

缓存击穿，是指一个key非常热点，在不停的扛着大并发，大并发集中对这一个点进行访问，当这个key在失效的瞬间，持续的大并发就穿破缓存，直接请求数据库，就像在一个屏障上凿开了一个洞。

当某个key在过期的瞬间，有大量的请求并发访问，这类数据一般是热点数据，由于缓存过期，会同时访问数据库来查询最新数据，并且回写缓存，会导使数据库瞬间压力过大。

解决方案：
1. 设置热点数据永不过期
从缓存层面来看，没有设置过期时间，所以不会出现热点 key 过期后产生的问题。
2. 加互斥锁
 
   分布式锁：使用分布式锁，保证对于每个key同时只有一个线程去查询后端服务，其他线程没有获得分布式锁的权限，因此只需要等待即可。这种方式将高并发的压力转移到了分布式锁，因此对分布式锁的考验很大。

举个例子：使用setnx实现分布式锁

- 加锁
 
最简单的方法是使用setnx命令。key是锁的唯一标识，按业务来决定命名。比如想要给一种商品的秒杀活动加锁，可以给key命名为 “lock_sale_商品ID_x(第x个)” 。而value设置成什么呢？value其实并不重要，因为只要这个唯一的key-value存在，就表示这个商品已经上锁。我们可以姑且设置成1。加锁的伪代码如下：    
```bash
setnx（key，1）
```
当一个线程执行setnx返回1，说明key原本不存在，该线程成功得到了锁；当一个线程执行setnx返回0，说明key已经存在，该线程抢锁失败。

- 解锁
 
有加锁就得有解锁。当得到锁的线程执行完任务，需要释放锁，以便其他线程可以进入。释放锁的最简单方式是执行del指令，伪代码如下：
```bash
del（key）
```
释放锁之后，其他线程就可以继续执行setnx命令来获得锁。

#### 缓存血崩
是指在某一个时间段，缓存集中过期失效。

产生雪崩的原因之一，比如马上就要到双十二零点，很快就会迎来一波抢购，这波商品时间比较集中的放入了缓存，假设缓存一个小时。那么到了凌晨一点钟的时候，这批商品的缓存就都过期了。而对这批商品的访问查询，都落到了数据库上，对于数据库而言，就会产生周期性的压力波峰。于是所有的请求都会达到存储层，存储层的调用量会暴增，造成存储层也会挂掉的情况。

![缓存血崩](https://raw.githubusercontent.com/koshunho/koshunhopic/master/blog1595349116749.png)

其实集中过期，倒不是非常致命，比较致命的缓存雪崩，是缓存服务器某个节点宕机或断网。因为自然形成的缓存雪崩，一定是在某个时间段集中创建缓存，这个时候，数据库也是可以顶住压力的。无非就是对数据库产生周期性的压力而已。而==缓存服务节点的宕机==，对数据库服务器造成的压力是不可预知的，很有可能瞬间就把数据库压垮。

解决方法：
1. Redis高可用
这个思想的含义是，既然redis有可能挂掉，那我多增设几台redis，这样一台挂掉之后其他的还可以继续工作，其实就是搭建的**集群**。
`集群`
`集群`
`集群`
`集群`
`集群`
`集群`
`集群`
`集群`
`集群`
`集群`
`集群`
`集群`

2. 限流降级
这个解决方案的思想是，在缓存失效后，通过加锁或者队列来控制读数据库写缓存的线程数量。比如对某个key只允许一个线程查询数据和写缓存，其他线程等待。

3. **数据预热**
数据加热的含义就是在正式部署之前，我先把可能的数据先预先访问一遍，这样部分可能大量访问的数据就会加载到缓存中。在即将发生大并发访问前手动触发加载缓存不同的key，设置不同的过期时间，让缓存失效的时间点尽量均匀。