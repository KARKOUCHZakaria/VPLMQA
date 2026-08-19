package com.figma.design;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication(scanBasePackages = {"com.figma", "com.vplmqa.design"})
@ConfigurationPropertiesScan
public class FigmaToDesignServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(FigmaToDesignServiceApplication.class, args);
	}

}
