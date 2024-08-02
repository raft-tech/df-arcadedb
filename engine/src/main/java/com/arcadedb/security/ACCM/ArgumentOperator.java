package com.arcadedb.security.ACCM;

public enum ArgumentOperator {
    EQ,
    NEQ,
    ANY_OF, // Doc value is any of the following options

    // Doc value contains the following option
    CONTAINS,
    NOT_CONTAINS,

    FIELD_NOT_PRESENT,

    // provided values contains 

    GT,
    GT_EQ,
    LT,
    LT_EQ,

    // can expand to include geo_within, geo_intersects, etc.

    // array to array
    ANY_IN,
    ALL_IN,
    NONE_IN
}
