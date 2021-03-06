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

package org.lealone.hansql.common.types;

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

public final class MajorType implements Externalizable, Message<MajorType>, Schema<MajorType>
{

    public static Schema<MajorType> getSchema()
    {
        return DEFAULT_INSTANCE;
    }

    public static MajorType getDefaultInstance()
    {
        return DEFAULT_INSTANCE;
    }

    static final MajorType DEFAULT_INSTANCE = new MajorType();

    
    private MinorType minorType;
    private DataMode mode;
    private int width;
    private int precision;
    private int scale;
    private int timeZone;
    private List<MinorType> subType;

    public MajorType()
    {
        
    }

    // getters and setters

    // minorType

    public MinorType getMinorType()
    {
        return minorType == null ? MinorType.LATE : minorType;
    }

    public MajorType setMinorType(MinorType minorType)
    {
        this.minorType = minorType;
        return this;
    }

    // mode

    public DataMode getMode()
    {
        return mode == null ? DataMode.OPTIONAL : mode;
    }

    public MajorType setMode(DataMode mode)
    {
        this.mode = mode;
        return this;
    }

    // width

    public int getWidth()
    {
        return width;
    }

    public MajorType setWidth(int width)
    {
        this.width = width;
        return this;
    }

    // precision

    public int getPrecision()
    {
        return precision;
    }

    public MajorType setPrecision(int precision)
    {
        this.precision = precision;
        return this;
    }

    // scale

    public int getScale()
    {
        return scale;
    }

    public MajorType setScale(int scale)
    {
        this.scale = scale;
        return this;
    }

    // timeZone

    public int getTimeZone()
    {
        return timeZone;
    }

    public MajorType setTimeZone(int timeZone)
    {
        this.timeZone = timeZone;
        return this;
    }

    // subType

    public List<MinorType> getSubTypeList()
    {
        return subType;
    }

    public MajorType setSubTypeList(List<MinorType> subType)
    {
        this.subType = subType;
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

    public Schema<MajorType> cachedSchema()
    {
        return DEFAULT_INSTANCE;
    }

    // schema methods

    public MajorType newMessage()
    {
        return new MajorType();
    }

    public Class<MajorType> typeClass()
    {
        return MajorType.class;
    }

    public String messageName()
    {
        return MajorType.class.getSimpleName();
    }

    public String messageFullName()
    {
        return MajorType.class.getName();
    }

    public boolean isInitialized(MajorType message)
    {
        return true;
    }

    public void mergeFrom(Input input, MajorType message) throws IOException
    {
        for(int number = input.readFieldNumber(this);; number = input.readFieldNumber(this))
        {
            switch(number)
            {
                case 0:
                    return;
                case 1:
                    message.minorType = MinorType.valueOf(input.readEnum());
                    break;
                case 2:
                    message.mode = DataMode.valueOf(input.readEnum());
                    break;
                case 3:
                    message.width = input.readInt32();
                    break;
                case 4:
                    message.precision = input.readInt32();
                    break;
                case 5:
                    message.scale = input.readInt32();
                    break;
                case 6:
                    message.timeZone = input.readInt32();
                    break;
                case 7:
                    if(message.subType == null)
                        message.subType = new ArrayList<MinorType>();
                    message.subType.add(MinorType.valueOf(input.readEnum()));
                    break;
                default:
                    input.handleUnknownField(number, this);
            }   
        }
    }


    public void writeTo(Output output, MajorType message) throws IOException
    {
        if(message.minorType != null)
             output.writeEnum(1, message.minorType.number, false);

        if(message.mode != null)
             output.writeEnum(2, message.mode.number, false);

        if(message.width != 0)
            output.writeInt32(3, message.width, false);

        if(message.precision != 0)
            output.writeInt32(4, message.precision, false);

        if(message.scale != 0)
            output.writeInt32(5, message.scale, false);

        if(message.timeZone != 0)
            output.writeInt32(6, message.timeZone, false);

        if(message.subType != null)
        {
            for(MinorType subType : message.subType)
            {
                if(subType != null)
                    output.writeEnum(7, subType.number, true);
            }
        }
    }

    public String getFieldName(int number)
    {
        switch(number)
        {
            case 1: return "minorType";
            case 2: return "mode";
            case 3: return "width";
            case 4: return "precision";
            case 5: return "scale";
            case 6: return "timeZone";
            case 7: return "subType";
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
        __fieldMap.put("minorType", 1);
        __fieldMap.put("mode", 2);
        __fieldMap.put("width", 3);
        __fieldMap.put("precision", 4);
        __fieldMap.put("scale", 5);
        __fieldMap.put("timeZone", 6);
        __fieldMap.put("subType", 7);
    }
    
}
