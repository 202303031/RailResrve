package com.railreserve;

import org.springframework.boot.SpringApplication;

public class TestRailReserveApplication {

	public static void main(String[] args) {
		SpringApplication.from(RailReserveApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
