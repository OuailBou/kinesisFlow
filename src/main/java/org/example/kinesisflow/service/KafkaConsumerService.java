package org.example.kinesisflow.service;
import org.example.kinesisflow.record.cryptoEvent;
import org.example.kinesisflow.repository.AlertRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class KafkaConsumerService {

    private static final Logger log = LoggerFactory.getLogger(KafkaConsumerService.class);

    private final RedisStringService redisStringService;
    private final RedisSortedSetService redisSortedSetService;

    public KafkaConsumerService(RedisStringService redisStringService,  RedisSortedSetService redisSortedSetService) {
        this.redisStringService = redisStringService;
        this.redisSortedSetService = redisSortedSetService;

    }

    @KafkaListener(id = "kinesis-listener", topics = "data-injest", groupId = "kinesis-group")
    public void listen(cryptoEvent in) {
        log.info("message received {}", in);
        Double formerPrice= redisStringService.get(in.asset());


        if(formerPrice==null){
            redisStringService.save(in.asset(),in.price());
            return;

        }
        Set<String> values = new HashSet<>();
        String gtKey = redisSortedSetService.createRuleIndexKey(in.asset(), "1");
        String ltKey = redisSortedSetService.createRuleIndexKey(in.asset(), "-1");
        List<String> Userslist = new ArrayList<>();

        if(in.price().compareTo(BigDecimal.valueOf(formerPrice))>0){
            values =  redisSortedSetService.getRangeByScore(gtKey, BigDecimal.valueOf(formerPrice), in.price(), true, false);
            Userslist = values.stream().map(v -> v.split(":")[0]).toList();

        }
        else if(in.price().compareTo(BigDecimal.valueOf(formerPrice))<0){
            values =  redisSortedSetService.getRangeByScore(ltKey,in.price(), BigDecimal.valueOf(formerPrice), true, false);
            Userslist = values.stream().map(v -> v.split(":")[0]).toList();

        }
        redisStringService.save(in.asset(),in.price());



    }
}
