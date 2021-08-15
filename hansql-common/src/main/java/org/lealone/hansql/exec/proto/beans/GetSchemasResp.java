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
import java.util.ArrayList;
import java.util.List;

import com.dyuproject.protostuff.GraphIOUtil;
import com.dyuproject.protostuff.Input;
import com.dyuproject.protostuff.Message;
import com.dyuproject.protostuff.Output;
import com.dyuproject.protostuff.Schema;

public final class GetSchemasResp implements Externalizable, Message<GetSchemasResp>, Schema<GetSchemasResp>
{

    public static Schema<GetSchemasResp> getSchema()
    {
        return DEFAULT_INSTANCE;
    }

    public static GetSchemasResp getDefaultInstance()
    {
        return DEFAULT_INSTANCE;
    }

    static final GetSchemasResp DEFAULT_INSTANCE = new GetSchemasResp();

    
    private RequestStatus status;
    private List<SchemaMetadata> schemas;
    private DrillPBError error;

    public GetSchemasResp()
    {
        
    }

    // getters and setters

    // status

    public RequestStatus getStatus()
    {
        return status == null ? RequestStatus.UNKNOWN_STATUS : status;
    }

    public GetSchemasResp setStatus(RequestStatus status)
    {
        this.status = status;
        return this;
    }

    // schemas

    public List<SchemaMetadata> getSchemasList()
    {
        return schemas;
    }

    public GetSchemasResp setSchemasList(List<SchemaMetadata> schemas)
    {
        this.schemas = schemas;
        return this;
    }

    // error

    public DrillPBError getError()
    {
        return error;
    }

    public GetSchemasResp setError(DrillPBError error)
    {
        this.error = error;
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

    public Schema<GetSchemasResp> cachedSchema()
    {
        return DEFAULT_INSTANCE;
    }

    // schema methods

    public GetSchemasResp newMessage()
    {
        return new GetSchemasResp();
    }

    public Class<GetSchemasResp> typeClass()
    {
        return GetSchemasResp.class;
    }

    public String messageName()
    {
        return GetSchemasResp.class.getSimpleName();
    }

    public String messageFullName()
    {
        return GetSchemasResp.class.getName();
    }

    public boolean isInitialized(GetSchemasResp message)
    {
        return true;
    }

    public void mergeFrom(Input input, GetSchemasResp message) throws IOException
    {
        for(int number = input.readFieldNumber(this);; number = input.readFieldNumber(this))
        {
            switch(number)
            {
                case 0:
                    return;
                case 1:
                    message.status = RequestStatus.valueOf(input.readEnum());
                    break;
                case 2:
                    if(message.schemas == null)
                        message.schemas = new ArrayList<SchemaMetadata>();
                    message.schemas.add(input.mergeObject(null, SchemaMetadata.getSchema()));
                    break;

                case 3:
                    message.error = input.mergeObject(message.error, DrillPBError.getSchema());
                    break;

                default:
                    input.handleUnknownField(number, this);
            }   
        }
    }


    public void writeTo(Output output, GetSchemasResp message) throws IOException
    {
        if(message.status != null)
             output.writeEnum(1, message.status.number, false);

        if(message.schemas != null)
        {
            for(SchemaMetadata schemas : message.schemas)
            {
                if(schemas != null)
                    output.writeObject(2, schemas, SchemaMetadata.getSchema(), true);
            }
        }


        if(message.error != null)
             output.writeObject(3, message.error, DrillPBError.getSchema(), false);

    }

    public String getFieldName(int number)
    {
        switch(number)
        {
            case 1: return "status";
            case 2: return "schemas";
            case 3: return "error";
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
        __fieldMap.put("status", 1);
        __fieldMap.put("schemas", 2);
        __fieldMap.put("error", 3);
    }
    
}