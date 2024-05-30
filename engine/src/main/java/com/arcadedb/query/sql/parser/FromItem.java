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
/* Generated By:JJTree: Do not edit this line. OFromItem.java Version 4.3 */
/* JavaCCOptions:MULTI=true,NODE_USES_PARSER=false,VISITOR=true,TRACK_TOKENS=true,NODE_PREFIX=O,NODE_EXTENDS=,NODE_FACTORY=,SUPPORT_USERTYPE_VISIBILITY_PUBLIC=true */
package com.arcadedb.query.sql.parser;

import com.arcadedb.database.Identifiable;
import com.arcadedb.query.sql.executor.Result;
import com.arcadedb.query.sql.executor.ResultSet;

import java.util.*;
import java.util.stream.*;

public class FromItem extends SimpleNode {
  protected List<Rid>            rids;
  protected List<InputParameter> inputParams;
  protected ResultSet            resultSet;
  protected Bucket               bucket;
  protected BucketList           bucketList;
  protected IndexIdentifier      index;
  protected SchemaIdentifier     schema;
  protected Statement            statement;
  protected InputParameter       inputParam;
  protected Identifier           identifier;
  protected FunctionCall         functionCall;
  protected Modifier             modifier;

  public FromItem(final int id) {
    super(id);
  }

  public void toString(final Map<String, Object> params, final StringBuilder builder) {
    if (rids != null && rids.size() > 0) {
      if (rids.size() == 1) {
        rids.get(0).toString(params, builder);
        return;
      } else {
        builder.append("[");
        boolean first = true;
        for (final Rid rid : rids) {
          if (!first) {
            builder.append(", ");
          }
          rid.toString(params, builder);
          first = false;
        }
        builder.append("]");
        return;
      }
    } else if (inputParams != null && inputParams.size() > 0) {
      if (inputParams.size() == 1) {
        inputParams.get(0).toString(params, builder);
        return;
      } else {
        builder.append("[");
        boolean first = true;
        for (final InputParameter rid : inputParams) {
          if (!first) {
            builder.append(", ");
          }
          rid.toString(params, builder);
          first = false;
        }
        builder.append("]");
        return;
      }
    } else if (bucket != null) {
      bucket.toString(params, builder);
      return;
      // } else if (className != null) {
      // return className.getValue();
    } else if (bucketList != null) {
      bucketList.toString(params, builder);
      return;
    } else if (schema != null) {
      schema.toString(params, builder);
      return;
    } else if (statement != null) {
      builder.append("(");
      statement.toString(params, builder);
      builder.append(")");
      return;
    } else if (index != null) {
      index.toString(params, builder);
      return;
    } else if (inputParam != null) {
      inputParam.toString(params, builder);
    } else if (functionCall != null) {
      functionCall.toString(params, builder);
    } else if (identifier != null) {
      identifier.toString(params, builder);
    } else if (resultSet != null)
      builder.append("resultSet");

    if (modifier != null)
      modifier.toString(params, builder);
  }

  public Identifier getIdentifier() {
    return identifier;
  }

  public ResultSet getResultSet() {
    return resultSet;
  }

  public List<Rid> getRids() {
    return rids;
  }

  public Bucket getBucket() {
    return bucket;
  }

  public BucketList getBucketList() {
    return bucketList;
  }

  public IndexIdentifier getIndex() {
    return index;
  }

  public SchemaIdentifier getSchema() {
    return schema;
  }

  public Statement getStatement() {
    return statement;
  }

  public InputParameter getInputParam() {
    return inputParam;
  }

  public List<InputParameter> getInputParams() {
    return inputParams;
  }

  public FunctionCall getFunctionCall() {
    return functionCall;
  }

  public Modifier getModifier() {
    return modifier;
  }

  public FromItem copy() {
    final FromItem result = new FromItem(-1);
    if (rids != null) {
      result.rids = rids.stream().map(r -> r.copy()).collect(Collectors.toList());
    }
    if (inputParams != null) {
      result.inputParams = inputParams.stream().map(r -> r.copy()).collect(Collectors.toList());
    }
    result.bucket = bucket == null ? null : bucket.copy();
    result.bucketList = bucketList == null ? null : bucketList.copy();
    result.index = index == null ? null : index.copy();
    result.schema = schema == null ? null : schema.copy();
    result.statement = statement == null ? null : statement.copy();
    result.inputParam = inputParam == null ? null : inputParam.copy();
    result.identifier = identifier == null ? null : identifier.copy();
    result.functionCall = functionCall == null ? null : functionCall.copy();
    result.modifier = modifier == null ? null : modifier.copy();
    result.resultSet = resultSet == null ? null : resultSet.copy();

    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;

    final FromItem oFromItem = (FromItem) o;

    if (!Objects.equals(rids, oFromItem.rids))
      return false;
    if (!Objects.equals(inputParams, oFromItem.inputParams))
      return false;
    if (!Objects.equals(bucket, oFromItem.bucket))
      return false;
    if (!Objects.equals(bucketList, oFromItem.bucketList))
      return false;
    if (!Objects.equals(index, oFromItem.index))
      return false;
    if (!Objects.equals(schema, oFromItem.schema))
      return false;
    if (!Objects.equals(statement, oFromItem.statement))
      return false;
    if (!Objects.equals(inputParam, oFromItem.inputParam))
      return false;
    if (!Objects.equals(identifier, oFromItem.identifier))
      return false;
    if (!Objects.equals(functionCall, oFromItem.functionCall))
      return false;
    if (!Objects.equals(resultSet, oFromItem.resultSet))
      return false;
    return Objects.equals(modifier, oFromItem.modifier);
  }

  @Override
  public int hashCode() {
    int result = rids != null ? rids.hashCode() : 0;
    result = 31 * result + (inputParams != null ? inputParams.hashCode() : 0);
    result = 31 * result + (bucket != null ? bucket.hashCode() : 0);
    result = 31 * result + (bucketList != null ? bucketList.hashCode() : 0);
    result = 31 * result + (index != null ? index.hashCode() : 0);
    result = 31 * result + (schema != null ? schema.hashCode() : 0);
    result = 31 * result + (statement != null ? statement.hashCode() : 0);
    result = 31 * result + (inputParam != null ? inputParam.hashCode() : 0);
    result = 31 * result + (identifier != null ? identifier.hashCode() : 0);
    result = 31 * result + (functionCall != null ? functionCall.hashCode() : 0);
    result = 31 * result + (resultSet != null ? resultSet.hashCode() : 0);
    result = 31 * result + (modifier != null ? modifier.hashCode() : 0);
    return result;
  }

  public void setRids(final List<Rid> rids) {
    this.rids = rids;
  }

  public void setBucket(final Bucket bucket) {
    this.bucket = bucket;
  }

  public void setBucketList(final BucketList bucketList) {
    this.bucketList = bucketList;
  }

  public void setIndex(final IndexIdentifier index) {
    this.index = index;
  }

  public void setSchema(final SchemaIdentifier schema) {
    this.schema = schema;
  }

  public void setStatement(final Statement statement) {
    this.statement = statement;
  }

  public void setInputParam(final InputParameter inputParam) {
    this.inputParam = inputParam;
  }

  public void setIdentifier(final Identifier identifier) {
    this.identifier = identifier;
  }

  public void setFunctionCall(final FunctionCall functionCall) {
    this.functionCall = functionCall;
  }

  public void setModifier(final Modifier modifier) {
    this.modifier = modifier;
  }

  public void setInputParams(final List<InputParameter> inputParams) {
    this.inputParams = inputParams;
  }

  @Override
  public boolean isCacheable() {
    if (modifier != null)
      return false;

    if (inputParam != null)
      return false;

    if (inputParams != null && !inputParams.isEmpty())
      return false;

    if (statement != null)
      return statement.executionPlanCanBeCached();

    if (functionCall != null)
      return functionCall.isCacheable();

    return true;
  }

  public boolean refersToParent() {
    if (identifier != null && identifier.refersToParent())
      return true;

    if (modifier != null && modifier.refersToParent())
      return true;

    if (statement != null && statement.refersToParent())
      return true;

    return functionCall != null && functionCall.refersToParent();
  }

  public void setValue(final Object value) {
    if (value instanceof Identifiable)
      rids.add(new Rid(((Identifiable) value).getIdentity()));
    else if (value instanceof ResultSet) {
      resultSet = (ResultSet) value;
    } else if (value instanceof Result) {
      final Result r = (Result) value;
      if (r.isElement())
        setValue(((Result) value).toElement());
      else
        setValue(r.toMap());
    }
  }
}
/* JavaCC - OriginalChecksum=f64e3b4d2a2627a1b5d04a7dcb95fa94 (do not edit this line) */
