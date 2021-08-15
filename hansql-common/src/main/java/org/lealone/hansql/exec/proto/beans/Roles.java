/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
// Generated by http://code.google.com/p/protostuff/ ... DO NOT EDIT!
// Generated from protobuf

package org.lealone.hansql.exec.proto.beans;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

import com.dyuproject.protostuff.GraphIOUtil;
import com.dyuproject.protostuff.Input;
import com.dyuproject.protostuff.Message;
import com.dyuproject.protostuff.Output;
import com.dyuproject.protostuff.Schema;

public final class Roles implements Externalizable, Message<Roles>, Schema<Roles>
{

    public static Schema<Roles> getSchema()
    {
        return DEFAULT_INSTANCE;
    }

    public static Roles getDefaultInstance()
    {
        return DEFAULT_INSTANCE;
    }

    static final Roles DEFAULT_INSTANCE = new Roles();

    static final Boolean DEFAULT_SQL_QUERY = new Boolean(true);
    static final Boolean DEFAULT_LOGICAL_PLAN = new Boolean(true);
    static final Boolean DEFAULT_PHYSICAL_PLAN = new Boolean(true);
    static final Boolean DEFAULT_JAVA_EXECUTOR = new Boolean(true);
    static final Boolean DEFAULT_DISTRIBUTED_CACHE = new Boolean(true);
    
    private Boolean sqlQuery = DEFAULT_SQL_QUERY;
    private Boolean logicalPlan = DEFAULT_LOGICAL_PLAN;
    private Boolean physicalPlan = DEFAULT_PHYSICAL_PLAN;
    private Boolean javaExecutor = DEFAULT_JAVA_EXECUTOR;
    private Boolean distributedCache = DEFAULT_DISTRIBUTED_CACHE;

    public Roles()
    {
        
    }

    // getters and setters

    // sqlQuery

    public Boolean getSqlQuery()
    {
        return sqlQuery;
    }

    public Roles setSqlQuery(Boolean sqlQuery)
    {
        this.sqlQuery = sqlQuery;
        return this;
    }

    // logicalPlan

    public Boolean getLogicalPlan()
    {
        return logicalPlan;
    }

    public Roles setLogicalPlan(Boolean logicalPlan)
    {
        this.logicalPlan = logicalPlan;
        return this;
    }

    // physicalPlan

    public Boolean getPhysicalPlan()
    {
        return physicalPlan;
    }

    public Roles setPhysicalPlan(Boolean physicalPlan)
    {
        this.physicalPlan = physicalPlan;
        return this;
    }

    // javaExecutor

    public Boolean getJavaExecutor()
    {
        return javaExecutor;
    }

    public Roles setJavaExecutor(Boolean javaExecutor)
    {
        this.javaExecutor = javaExecutor;
        return this;
    }

    // distributedCache

    public Boolean getDistributedCache()
    {
        return distributedCache;
    }

    public Roles setDistributedCache(Boolean distributedCache)
    {
        this.distributedCache = distributedCache;
        return this;
    }

    // java serialization

    public void readExternal(ObjectInput in) throws IOException
    {
        GraphIOUtil.mergeDelimitedFrom(in, this, this);
    }

    public void writeExternal(ObjectOutput out) throws IOException
    {
        GraphIOUtil.writeDelimitedTo(out, this, this);
    }

    // message method

    public Schema<Roles> cachedSchema()
    {
        return DEFAULT_INSTANCE;
    }

    // schema methods

    public Roles newMessage()
    {
        return new Roles();
    }

    public Class<Roles> typeClass()
    {
        return Roles.class;
    }

    public String messageName()
    {
        return Roles.class.getSimpleName();
    }

    public String messageFullName()
    {
        return Roles.class.getName();
    }

    public boolean isInitialized(Roles message)
    {
        return true;
    }

    public void mergeFrom(Input input, Roles message) throws IOException
    {
        for(int number = input.readFieldNumber(this);; number = input.readFieldNumber(this))
        {
            switch(number)
            {
                case 0:
                    return;
                case 1:
                    message.sqlQuery = input.readBool();
                    break;
                case 2:
                    message.logicalPlan = input.readBool();
                    break;
                case 3:
                    message.physicalPlan = input.readBool();
                    break;
                case 4:
                    message.javaExecutor = input.readBool();
                    break;
                case 5:
                    message.distributedCache = input.readBool();
                    break;
                default:
                    input.handleUnknownField(number, this);
            }   
        }
    }


    public void writeTo(Output output, Roles message) throws IOException
    {
        if(message.sqlQuery != null && message.sqlQuery != DEFAULT_SQL_QUERY)
            output.writeBool(1, message.sqlQuery, false);

        if(message.logicalPlan != null && message.logicalPlan != DEFAULT_LOGICAL_PLAN)
            output.writeBool(2, message.logicalPlan, false);

        if(message.physicalPlan != null && message.physicalPlan != DEFAULT_PHYSICAL_PLAN)
            output.writeBool(3, message.physicalPlan, false);

        if(message.javaExecutor != null && message.javaExecutor != DEFAULT_JAVA_EXECUTOR)
            output.writeBool(4, message.javaExecutor, false);

        if(message.distributedCache != null && message.distributedCache != DEFAULT_DISTRIBUTED_CACHE)
            output.writeBool(5, message.distributedCache, false);
    }

    public String getFieldName(int number)
    {
        switch(number)
        {
            case 1: return "sqlQuery";
            case 2: return "logicalPlan";
            case 3: return "physicalPlan";
            case 4: return "javaExecutor";
            case 5: return "distributedCache";
            default: return null;
        }
    }

    public int getFieldNumber(String name)
    {
        final Integer number = __fieldMap.get(name);
        return number == null ? 0 : number.intValue();
    }

    private static final java.util.HashMap<String,Integer> __fieldMap = new java.util.HashMap<String,Integer>();
    static
    {
        __fieldMap.put("sqlQuery", 1);
        __fieldMap.put("logicalPlan", 2);
        __fieldMap.put("physicalPlan", 3);
        __fieldMap.put("javaExecutor", 4);
        __fieldMap.put("distributedCache", 5);
    }
    
}