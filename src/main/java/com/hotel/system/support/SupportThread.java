package com.hotel.system.support;

import java.util.List;

public record SupportThread(SupportCase supportCase, List<SupportMessage> messages) { }
