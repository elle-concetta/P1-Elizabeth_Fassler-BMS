package com.example.banking.app;

import com.example.banking.util.LoggerFactory;
import org.slf4j.Logger;

public final class BankingApplication {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(BankingApplication.class);

    private BankingApplication() {
    }

    public static void main(String[] args) {
        LOGGER.info(
                "Banking CLI started; banking workflows are not implemented yet."
        );

        System.out.println("Secure Banking CLI — scaffold only.");

        System.out.println(
                "Banking features are not implemented. "
                        + "See README.md and the supplied design."
        );
    }
}
