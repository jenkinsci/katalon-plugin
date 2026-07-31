package com.katalon.utils;

class ConsoleLogger implements Logger {

    @Override
    public void info(String message) {
        System.out.println(message);
    }
}
