package com.chinasofti.huateng.acc.security.server.socket;

import java.util.List;

public record RawResponseSpec(boolean reserved, List<RawResponseFieldSpec> fields) {
}
