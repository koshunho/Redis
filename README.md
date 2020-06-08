# Redis

## RedisConfig
RedisAutoConfiguration自动配置类中在容器中生成了一个RedisTemplate和一个StringRedisTemplate。
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
但是，这个RedisTemplate的泛型是<Object,Object>，写代码不方便，需要写好多类型转换的代码；我
们需要一个泛型为<String,Object>形式的RedisTemplate。

并且，这个RedisTemplate没有设置数据存在Redis时，key及value的序列化方式。
看到这个@ConditionalOnMissingBean注解后，就知道如果Spring容器中有了RedisTemplate对象了，
这个自动配置的RedisTemplate不会实例化。因此我们可以直接自己写个配置类，配置
RedisTemplate。

在自己写的RedisTemplate中，key 和 hash的key 都采用String的序列化方式。

value 和 hash的value 都采用jackson的序列化方式。

这样就可以解决掉在redis-cli出现乱码的问题。

P.S. 但是在redis-cli中，get key时还是会出现乱码。查了说在启动时输入redis-cli.exe **--raw** 可以解决，但还是乱码- -
最后发现是cmd的编码问题，在启动之前切换到utf-8编码就好：**CHCP 65001**

此外，原本配置template中的enableDefaultTyping()过期了，因此改用mapper.activateDefaultTyping(mapper.getPolymorphicTypeValidator());

## RedisUtils
直接用RedisTemplate操作Redis，需要很多行代码，因此直接封装好一个
RedisUtils。



