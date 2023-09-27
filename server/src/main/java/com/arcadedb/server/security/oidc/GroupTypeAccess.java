package com.arcadedb.server.security.oidc;

import java.util.ArrayList;
import java.util.List;

import com.arcadedb.server.security.oidc.role.CRUDPermission;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GroupTypeAccess {
    List<CRUDPermission> access = new ArrayList<>();
}
