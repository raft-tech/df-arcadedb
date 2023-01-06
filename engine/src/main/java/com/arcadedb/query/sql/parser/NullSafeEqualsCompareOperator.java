/*
 * Copyright © 2021-present Arcade Data Ltd (info@arcadedata.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-FileCopyrightText: 2021-present Arcade Data Ltd (info@arcadedata.com)
 * SPDX-License-Identifier: Apache-2.0
 */
/* Generated By:JJTree: Do not edit this line. NullSafeEqualsCompareOperator.java Version 4.3 */
/* JavaCCOptions:MULTI=true,NODE_USES_PARSER=false,VISITOR=true,TRACK_TOKENS=true,NODE_PREFIX=O,NODE_EXTENDS=,NODE_FACTORY=,SUPPORT_USERTYPE_VISIBILITY_PUBLIC=true */
package com.arcadedb.query.sql.parser;

import com.arcadedb.database.DatabaseInternal;
import com.arcadedb.query.sql.executor.QueryOperatorEquals;

public class NullSafeEqualsCompareOperator extends SimpleNode implements BinaryCompareOperator {

  public NullSafeEqualsCompareOperator(final int id) {
    super(id);
  }

  public NullSafeEqualsCompareOperator(final SqlParser p, final int id) {
    super(p, id);
  }

  @Override
  public boolean execute(final DatabaseInternal database, final Object iLeft, final Object iRight) {
    if (iLeft == null && iRight == null)
      return true;

    return QueryOperatorEquals.equals(iLeft, iRight);
  }

  @Override
  public boolean supportsBasicCalculation() {
    return true;
  }

  @Override
  public String toString() {
    return "<=>";
  }

  @Override
  public NullSafeEqualsCompareOperator copy() {
    return this;
  }

  @Override
  public boolean equals(final Object obj) {
    return obj != null && obj.getClass().equals(this.getClass());
  }

  @Override
  public int hashCode() {
    return getClass().hashCode();
  }
}
/* JavaCC - OriginalChecksum=bd2ec5d13a1d171779c2bdbc9d3a56bc (do not edit this line) */