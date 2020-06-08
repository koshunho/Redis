package com.huang;

import com.huang.utils.RedisUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class RedisSpringbootApplicationTests {

    @Autowired
    private RedisUtil redisUtil;

    @Test
    void contextLoads() {
        redisUtil.set("mykey","小黄");
        String str = (String) redisUtil.get("mykey");
        System.out.println(str);
    }

}
