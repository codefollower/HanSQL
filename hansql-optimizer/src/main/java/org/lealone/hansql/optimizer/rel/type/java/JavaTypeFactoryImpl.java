/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to you under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.lealone.hansql.optimizer.rel.type.java;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.lealone.hansql.optimizer.rel.type.RelDataType;
import org.lealone.hansql.optimizer.rel.type.RelDataTypeFactory;
import org.lealone.hansql.optimizer.rel.type.RelDataTypeField;
import org.lealone.hansql.optimizer.rel.type.RelDataTypeFieldImpl;
import org.lealone.hansql.optimizer.rel.type.RelDataTypeSystem;
import org.lealone.hansql.optimizer.rel.type.RelRecordType;
import org.lealone.hansql.optimizer.sql.type.BasicSqlType;
import org.lealone.hansql.optimizer.sql.type.IntervalSqlType;
import org.lealone.hansql.optimizer.sql.type.JavaToSqlTypeConversionRules;
import org.lealone.hansql.optimizer.sql.type.SqlTypeFactoryImpl;
import org.lealone.hansql.optimizer.sql.type.SqlTypeName;
import org.lealone.hansql.optimizer.util.ByteString;
import org.lealone.hansql.optimizer.util.GeoFunctions;
import org.lealone.hansql.optimizer.util.Ord;
import org.lealone.hansql.optimizer.util.Pair;
import org.lealone.hansql.optimizer.util.Unit;
import org.lealone.hansql.optimizer.util.Util;

import com.google.common.collect.Lists;

/**
 * Implementation of {@link JavaTypeFactory}.
 *
 * <p><strong>NOTE: This class is experimental and subject to
 * change/removal without notice</strong>.</p>
 */
public class JavaTypeFactoryImpl extends SqlTypeFactoryImpl implements JavaTypeFactory {
    private final Map<List<Pair<Type, Boolean>>, SyntheticRecordType> syntheticTypes = new HashMap<>();

    public JavaTypeFactoryImpl() {
        this(RelDataTypeSystem.DEFAULT);
    }

    public JavaTypeFactoryImpl(RelDataTypeSystem typeSystem) {
        super(typeSystem);
    }

    @Override
    public RelDataType createStructType(Class<?> type) {
        final List<RelDataTypeField> list = new ArrayList<>();
        for (Field field : type.getFields()) {
            if (!Modifier.isStatic(field.getModifiers())) {
                // FIXME: watch out for recursion
                final Type fieldType = fieldType(field);
                list.add(new RelDataTypeFieldImpl(field.getName(), list.size(), createType(fieldType)));
            }
        }
        return canonize(new JavaRecordType(list, type));
    }

    /** Returns the type of a field.
     *
     * <p>Takes into account {@link org.lealone.hansql.optimizer.rel.type.annotations.Array}
     * annotations if present.
     */
    private Type fieldType(Field field) {
        final Class<?> klass = field.getType();
        final org.lealone.hansql.optimizer.rel.type.annotations.Array array = field
                .getAnnotation(org.lealone.hansql.optimizer.rel.type.annotations.Array.class);
        if (array != null) {
            return new Types.ArrayType(array.component(), array.componentIsNullable(), array.maximumCardinality());
        }
        final org.lealone.hansql.optimizer.rel.type.annotations.Map map = field
                .getAnnotation(org.lealone.hansql.optimizer.rel.type.annotations.Map.class);
        if (map != null) {
            return new Types.MapType(map.key(), map.keyIsNullable(), map.value(), map.valueIsNullable());
        }
        return klass;
    }

    @Override
    public RelDataType createType(Type type) {
        if (type instanceof RelDataType) {
            return (RelDataType) type;
        }
        if (type instanceof SyntheticRecordType) {
            final SyntheticRecordType syntheticRecordType = (SyntheticRecordType) type;
            return syntheticRecordType.relType;
        }
        if (type instanceof Types.ArrayType) {
            final Types.ArrayType arrayType = (Types.ArrayType) type;
            final RelDataType componentRelType = createType(arrayType.getComponentType());
            return createArrayType(createTypeWithNullability(componentRelType, arrayType.componentIsNullable()),
                    arrayType.maximumCardinality());
        }
        if (type instanceof Types.MapType) {
            final Types.MapType mapType = (Types.MapType) type;
            final RelDataType keyRelType = createType(mapType.getKeyType());
            final RelDataType valueRelType = createType(mapType.getValueType());
            return createMapType(createTypeWithNullability(keyRelType, mapType.keyIsNullable()),
                    createTypeWithNullability(valueRelType, mapType.valueIsNullable()));
        }
        if (!(type instanceof Class)) {
            throw new UnsupportedOperationException("TODO: implement " + type);
        }
        final Class<?> clazz = (Class<?>) type;
        switch (Primitive.flavor(clazz)) {
        case PRIMITIVE:
            return createJavaType(clazz);
        case BOX:
            return createJavaType(Primitive.ofBox(clazz).boxClass);
        }
        if (JavaToSqlTypeConversionRules.instance().lookup(clazz) != null) {
            return createJavaType(clazz);
        } else if (clazz.isArray()) {
            return createMultisetType(createType(clazz.getComponentType()), -1);
        } else if (List.class.isAssignableFrom(clazz)) {
            return createArrayType(createTypeWithNullability(createSqlType(SqlTypeName.ANY), true), -1);
        } else if (Map.class.isAssignableFrom(clazz)) {
            return createMapType(createTypeWithNullability(createSqlType(SqlTypeName.ANY), true),
                    createTypeWithNullability(createSqlType(SqlTypeName.ANY), true));
        } else {
            return createStructType(clazz);
        }
    }

    @Override
    public Type getJavaClass(RelDataType type) {
        if (type instanceof JavaType) {
            JavaType javaType = (JavaType) type;
            return javaType.getJavaClass();
        }
        if (type instanceof BasicSqlType || type instanceof IntervalSqlType) {
            switch (type.getSqlTypeName()) {
            case VARCHAR:
            case CHAR:
                return String.class;
            case DATE:
            case TIME:
            case TIME_WITH_LOCAL_TIME_ZONE:
            case INTEGER:
            case INTERVAL_YEAR:
            case INTERVAL_YEAR_MONTH:
            case INTERVAL_MONTH:
                return type.isNullable() ? Integer.class : int.class;
            case TIMESTAMP:
            case TIMESTAMP_WITH_LOCAL_TIME_ZONE:
            case BIGINT:
            case INTERVAL_DAY:
            case INTERVAL_DAY_HOUR:
            case INTERVAL_DAY_MINUTE:
            case INTERVAL_DAY_SECOND:
            case INTERVAL_HOUR:
            case INTERVAL_HOUR_MINUTE:
            case INTERVAL_HOUR_SECOND:
            case INTERVAL_MINUTE:
            case INTERVAL_MINUTE_SECOND:
            case INTERVAL_SECOND:
                return type.isNullable() ? Long.class : long.class;
            case SMALLINT:
                return type.isNullable() ? Short.class : short.class;
            case TINYINT:
                return type.isNullable() ? Byte.class : byte.class;
            case DECIMAL:
                return BigDecimal.class;
            case BOOLEAN:
                return type.isNullable() ? Boolean.class : boolean.class;
            case DOUBLE:
            case FLOAT: // sic
                return type.isNullable() ? Double.class : double.class;
            case REAL:
                return type.isNullable() ? Float.class : float.class;
            case BINARY:
            case VARBINARY:
                return ByteString.class;
            case GEOMETRY:
                return GeoFunctions.Geom.class;
            case SYMBOL:
                return Enum.class;
            case ANY:
                return Object.class;
            }
        }
        switch (type.getSqlTypeName()) {
        case ROW:
            assert type instanceof RelRecordType;
            if (type instanceof JavaRecordType) {
                return ((JavaRecordType) type).clazz;
            } else {
                return createSyntheticType((RelRecordType) type);
            }
        case MAP:
            return Map.class;
        case ARRAY:
        case MULTISET:
            return List.class;
        }
        return null;
    }

    @Override
    public RelDataType toSql(RelDataType type) {
        return toSql(this, type);
    }

    /** Converts a type in Java format to a SQL-oriented type. */
    public static RelDataType toSql(final RelDataTypeFactory typeFactory, RelDataType type) {
        return toSql(typeFactory, type, true);
    }

    private static RelDataType toSql(final RelDataTypeFactory typeFactory, RelDataType type,
            boolean mustSetNullability) {
        RelDataType sqlType = type;
        if (type instanceof RelRecordType) {
            // We do not need to change the nullability of the nested fields,
            // since it can be overridden by the existing implementation of createTypeWithNullability
            // when we treat the nullability of the root struct type.
            sqlType = typeFactory.createStructType(
                    Lists.transform(type.getFieldList(), field -> toSql(typeFactory, field.getType(), false)),
                    type.getFieldNames());
        } else if (type instanceof JavaType) {
            sqlType = typeFactory.createSqlType(type.getSqlTypeName());
        }
        return mustSetNullability ? typeFactory.createTypeWithNullability(sqlType, type.isNullable()) : sqlType;
    }

    @Override
    public Type createSyntheticType(List<Type> types) {
        if (types.isEmpty()) {
            // Unit is a pre-defined synthetic type to be used when there are 0
            // fields. Because all instances are the same, we use a singleton.
            return Unit.class;
        }
        final String name = "Record" + types.size() + "_" + syntheticTypes.size();
        final SyntheticRecordType syntheticType = new SyntheticRecordType(null, name);
        for (final Ord<Type> ord : Ord.zip(types)) {
            syntheticType.fields
                    .add(new RecordFieldImpl(syntheticType, "f" + ord.i, ord.e, !Primitive.is(ord.e), Modifier.PUBLIC));
        }
        return register(syntheticType);
    }

    private SyntheticRecordType register(final SyntheticRecordType syntheticType) {
        final List<Pair<Type, Boolean>> key = new AbstractList<Pair<Type, Boolean>>() {
            @Override
            public Pair<Type, Boolean> get(int index) {
                final Types.RecordField field = syntheticType.getRecordFields().get(index);
                return Pair.of(field.getType(), field.nullable());
            }

            @Override
            public int size() {
                return syntheticType.getRecordFields().size();
            }
        };
        SyntheticRecordType syntheticType2 = syntheticTypes.get(key);
        if (syntheticType2 == null) {
            syntheticTypes.put(key, syntheticType);
            return syntheticType;
        } else {
            return syntheticType2;
        }
    }

    /** Creates a synthetic Java class whose fields have the same names and
     * relational types. */
    private Type createSyntheticType(RelRecordType type) {
        final String name = "Record" + type.getFieldCount() + "_" + syntheticTypes.size();
        final SyntheticRecordType syntheticType = new SyntheticRecordType(type, name);
        for (final RelDataTypeField recordField : type.getFieldList()) {
            final Type javaClass = getJavaClass(recordField.getType());
            syntheticType.fields.add(new RecordFieldImpl(syntheticType, recordField.getName(), javaClass,
                    recordField.getType().isNullable() && !Primitive.is(javaClass), Modifier.PUBLIC));
        }
        return register(syntheticType);
    }

    /** Synthetic record type. */
    public static class SyntheticRecordType implements Types.RecordType {
        final List<Types.RecordField> fields = new ArrayList<>();
        final RelDataType relType;
        private final String name;

        private SyntheticRecordType(RelDataType relType, String name) {
            this.relType = relType;
            this.name = name;
            assert relType == null || Util.isDistinct(relType.getFieldNames()) : "field names not distinct: " + relType;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public List<Types.RecordField> getRecordFields() {
            return fields;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    /** Implementation of a field. */
    private static class RecordFieldImpl implements Types.RecordField {
        private final SyntheticRecordType syntheticType;
        private final String name;
        private final Type type;
        private final boolean nullable;
        private final int modifiers;

        RecordFieldImpl(SyntheticRecordType syntheticType, String name, Type type, boolean nullable, int modifiers) {
            this.syntheticType = Objects.requireNonNull(syntheticType);
            this.name = Objects.requireNonNull(name);
            this.type = Objects.requireNonNull(type);
            this.nullable = nullable;
            this.modifiers = modifiers;
            assert !(nullable && Primitive.is(type)) : "type [" + type + "] can never be null";
        }

        @Override
        public Type getType() {
            return type;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public int getModifiers() {
            return modifiers;
        }

        @Override
        public boolean nullable() {
            return nullable;
        }

        @Override
        public Object get(Object o) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Type getDeclaringClass() {
            return syntheticType;
        }
    }
}

// End JavaTypeFactoryImpl.java
