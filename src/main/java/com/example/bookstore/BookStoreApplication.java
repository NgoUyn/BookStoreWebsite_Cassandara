package com.example.bookstore;

import com.example.bookstore.config.mongo.MongoDevBootstrap;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class BookStoreApplication {

    public static void main(String[] args) {
        // DEV: dam bao MongoDB (single-node replica set, mac dinh cong 27018) da chay
        // TRUOC khi Spring khoi dong -> bam Run 1 lan la chay het.
        // Tat bang: --app.mongo.autostart.enabled=false (xem MongoDevBootstrap).
        MongoDevBootstrap.prepare(args);
        SpringApplication.run(BookStoreApplication.class, args);
    }


}
