package com.accrediator;

import java.awt.AWTException;
import java.awt.Robot;
import java.util.Random;

public class Halt {

	public static void main(String[] args) throws AWTException {

		
		Robot hal= new Robot();
		
		Random rand=new Random();
		while(true) {
			hal.delay(60*1000);
			int x=rand.nextInt() %640;
			
			int y=rand.nextInt()% 480;
			
			hal.mouseMove(x, y);
		}
	}

}
