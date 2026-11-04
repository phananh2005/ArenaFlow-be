package com.phananh.arenaflow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class ArenaFlowApplication {

	public static void main(String[] args) {
		SpringApplication.run(ArenaFlowApplication.class, args);
	}

}
