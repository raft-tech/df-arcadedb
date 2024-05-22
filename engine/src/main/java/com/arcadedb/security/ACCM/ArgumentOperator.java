package com.arcadedb.security.ACCM;

public enum ArgumentOperator {
    EQ,
    NEQ,
    ANY_OF,

    GT,
    GT_EQ,
    LT,
    LT_EQ,

    // can expand to include geo_within, geo_intersects, etc.

    ANY_IN,
    ALL_IN,
    NONE_IN
}
