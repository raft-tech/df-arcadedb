package com.arcadedb.security.ACCM;

import java.util.ArrayList;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import com.arcadedb.serializer.json.JSONObject;

// user: patrick
/*
 * [
 *   IIR
 *     create
 *     read
 *   People
 * 
 * ]
 * 
 * 
 * 
 */


@Data
@NoArgsConstructor
@AllArgsConstructor
public class TypeRestriction {
    
    private String name; // IIR
    private GraphType type; // Vertex
    private List<Expression> create = new ArrayList<>(); 
    private List<Expression> read = new ArrayList<>();
    private List<Expression> update = new ArrayList<>();
    private List<Expression> delete = new ArrayList<>();

    public boolean evaluateCreateRestrictions(JSONObject json) {
        return evaluateRestrictions(create, json);
    }

    public boolean evaluateReadRestrictions(JSONObject json) {
        return evaluateRestrictions(read, json);
    }

    public boolean evaluateUpdateRestrictions(JSONObject json) {
        return evaluateRestrictions(update, json);
    }

    public boolean evaluateDeleteRestrictions(JSONObject json) {
        return evaluateRestrictions(delete, json);
    }

    private boolean evaluateRestrictions(List<Expression> restrictions, JSONObject json) {
        boolean result = true;
        for (Expression restriction : restrictions) {
            result = result && restriction.evaluate(json);
        }
        return result;
    }
}
