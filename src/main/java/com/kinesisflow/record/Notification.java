package com.kinesisflow.record;

import java.math.BigDecimal;

public record Notification(
       String asset,
       BigDecimal price,
       String user
) {


}
