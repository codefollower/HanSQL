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
package org.lealone.hansql.exec.expr.fn.registry;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.drill.shaded.guava.com.google.common.collect.ImmutableMap;
import org.apache.drill.shaded.guava.com.google.common.collect.ListMultimap;
import org.apache.drill.shaded.guava.com.google.common.collect.Lists;
import org.apache.drill.shaded.guava.com.google.common.collect.Maps;
import org.apache.drill.shaded.guava.com.google.common.collect.Sets;
import org.lealone.hansql.common.scanner.persistence.AnnotatedClassDescriptor;
import org.lealone.hansql.common.scanner.persistence.ScanResult;
import org.lealone.hansql.exec.exception.FunctionValidationException;
import org.lealone.hansql.exec.exception.JarValidationException;
import org.lealone.hansql.exec.expr.annotations.FunctionTemplate;
import org.lealone.hansql.exec.expr.fn.DrillFuncHolder;
import org.lealone.hansql.exec.expr.fn.FunctionConverter;
import org.lealone.hansql.exec.planner.logical.DrillConstExecutor;
import org.lealone.hansql.exec.planner.sql.DrillOperatorTable;
import org.lealone.hansql.exec.planner.sql.DrillSqlAggOperator;
import org.lealone.hansql.exec.planner.sql.DrillSqlAggOperatorWithoutInference;
import org.lealone.hansql.exec.planner.sql.DrillSqlOperator;
import org.lealone.hansql.exec.planner.sql.DrillSqlOperatorWithoutInference;
import org.lealone.hansql.exec.store.sys.store.DataChangeVersion;
import org.lealone.hansql.optimizer.sql.SqlOperator;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.drill.shaded.guava.com.google.common.collect.ArrayListMultimap;

/**
 * Registry of Drill functions.
 */
public class LocalFunctionRegistry {

  public static final String BUILT_IN = "built-in";

  private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(LocalFunctionRegistry.class);
  private static final String functionSignaturePattern = "%s(%s)";

  private static final ImmutableMap<String, Pair<Integer, Integer>> registeredFuncNameToArgRange = ImmutableMap.<String, Pair<Integer, Integer>> builder()
      // CONCAT is allowed to take [1, infinity) number of arguments.
      // Currently, this flexibility is offered by DrillOptiq to rewrite it as
      // a nested structure
      .put("CONCAT", Pair.of(1, Integer.MAX_VALUE))

      // When LENGTH is given two arguments, this function relies on DrillOptiq to rewrite it as
      // another function based on the second argument (encodingType)
      .put("LENGTH", Pair.of(1, 2))

      // Dummy functions
      .put("CONVERT_TO", Pair.of(2, 2))
      .put("CONVERT_FROM", Pair.of(2, 2))
      .put("FLATTEN", Pair.of(1, 1)).build();

  private final FunctionRegistryHolder registryHolder;

  /**
   * Registers all functions present in Drill classpath on start-up.
   * All functions will be marked as built-in. Built-in functions are not allowed to be unregistered.
   * Since local function registry version is based on remote function registry version,
   * initially sync version will be set to {@link DataChangeVersion#UNDEFINED}
   * to ensure that upon first check both registries would be synchronized.
   */
  public LocalFunctionRegistry(ScanResult classpathScan) {
    registryHolder = new FunctionRegistryHolder();
    validate(BUILT_IN, classpathScan);
    register(Lists.newArrayList(new JarScan(BUILT_IN, classpathScan, this.getClass().getClassLoader())), DataChangeVersion.UNDEFINED);
    if (logger.isTraceEnabled()) {
      StringBuilder allFunctions = new StringBuilder();
      for (DrillFuncHolder method: registryHolder.getAllFunctionsWithHolders().values()) {
        allFunctions.append(method.toString()).append("\n");
      }
      logger.trace("Registered functions: [\n{}]", allFunctions);
    }
  }

  /**
   * @return remote function registry version number with which local function registry is synced
   */
  public int getVersion() {
    return registryHolder.getVersion();
  }

  /**
   * Validates all functions, present in jars.
   * Will throw {@link FunctionValidationException} if:
   * <ol>
   *  <li>Jar with the same name has been already registered.</li>
   *  <li>Conflicting function with the similar signature is found.</li>
   *  <li>Aggregating function is not deterministic.</li>
   *</ol>
   * @param jarName jar name to be validated
   * @param scanResult scan of all classes present in jar
   * @return list of validated function signatures
   */
  public List<String> validate(String jarName, ScanResult scanResult) {
    List<String> functions = Lists.newArrayList();
    FunctionConverter converter = new FunctionConverter();
    List<AnnotatedClassDescriptor> providerClasses = scanResult.getAnnotatedClasses(FunctionTemplate.class.getName());

    if (registryHolder.containsJar(jarName)) {
      throw new JarValidationException(String.format("Jar with %s name has been already registered", jarName));
    }

    final ListMultimap<String, String> allFuncWithSignatures = registryHolder.getAllFunctionsWithSignatures();

    for (AnnotatedClassDescriptor func : providerClasses) {
      DrillFuncHolder holder = converter.getHolder(func, ClassLoader.getSystemClassLoader());
      if (holder != null) {
        String functionInput = holder.getInputParameters();

        String[] names = holder.getRegisteredNames();
        for (String name : names) {
          String functionName = name.toLowerCase();
          String functionSignature = String.format(functionSignaturePattern, functionName, functionInput);

          if (allFuncWithSignatures.get(functionName).contains(functionSignature)) {
            throw new FunctionValidationException(String.format("Found duplicated function in %s: %s",
                registryHolder.getJarNameByFunctionSignature(functionName, functionSignature), functionSignature));
          } else if (holder.isAggregating() && !holder.isDeterministic()) {
            throw new FunctionValidationException(
                String.format("Aggregate functions must be deterministic: %s", func.getClassName()));
          } else {
            functions.add(functionSignature);
            allFuncWithSignatures.put(functionName, functionSignature);
          }
        }
      } else {
        logger.warn("Unable to initialize function for class {}", func.getClassName());
      }
    }
    return functions;
  }

  /**
   * Registers all functions present in jar and updates registry version.
   * If jar name is already registered, all jar related functions will be overridden.
   * To prevent classpath collisions during loading and unloading jars,
   * each jar is shipped with its own class loader.
   *
   * @param jars list of jars to be registered
   * @param version remote function registry version number with which local function registry is synced
   */
  public void register(List<JarScan> jars, int version) {
    Map<String, List<FunctionHolder>> newJars = new HashMap<>();
    for (JarScan jarScan : jars) {
      FunctionConverter converter = new FunctionConverter();
      List<AnnotatedClassDescriptor> providerClasses = jarScan.getScanResult().getAnnotatedClasses(FunctionTemplate.class.getName());
      List<FunctionHolder> functions = new ArrayList<>();
      newJars.put(jarScan.getJarName(), functions);
      for (AnnotatedClassDescriptor func : providerClasses) {
        DrillFuncHolder holder = converter.getHolder(func, jarScan.getClassLoader());
        if (holder != null) {
          String functionInput = holder.getInputParameters();
          String[] names = holder.getRegisteredNames();
          for (String name : names) {
            String functionName = name.toLowerCase();
            String functionSignature = String.format(functionSignaturePattern, functionName, functionInput);
            functions.add(new FunctionHolder(functionName, functionSignature, holder));
          }
        }
      }
    }
    registryHolder.addJars(newJars, version);
  }

  /**
   * Removes all function associated with the given jar name.
   * Functions marked as built-in is not allowed to be unregistered.
   * If user attempts to unregister built-in functions, logs warning and does nothing.
   * Jar name is case-sensitive.
   *
   * @param jarName jar name to be unregistered
   */
  public void unregister(String jarName) {
    if (BUILT_IN.equals(jarName)) {
      logger.warn("Functions marked as built-in are not allowed to be unregistered.");
      return;
    }
    registryHolder.removeJar(jarName);
  }

  /**
   * Returns list of jar names registered in function registry.
   *
   * @return list of jar names
   */
  public List<String> getAllJarNames() {
    return registryHolder.getAllJarNames();
  }

  /**
   * @return quantity of all registered functions
   */
  public int size(){
    return registryHolder.functionsSize();
  }

  /**
   * @param name function name
   * @return all function holders associated with the function name. Function name is case insensitive.
   */
  public List<DrillFuncHolder> getMethods(String name, AtomicInteger version) {
    return registryHolder.getHoldersByFunctionName(name.toLowerCase(), version);
  }

  /**
   * @param name function name
   * @return all function holders associated with the function name. Function name is case insensitive.
   */
  public List<DrillFuncHolder> getMethods(String name) {
    return registryHolder.getHoldersByFunctionName(name.toLowerCase());
  }

  /**
   * Returns a map of all function holders mapped by source jars
   * @return all functions organized by source jars
   */
  public Map<String, List<FunctionHolder>> getAllJarsWithFunctionsHolders() {
    return registryHolder.getAllJarsWithFunctionHolders();
  }

  /**
   * Registers all functions present in {@link DrillOperatorTable},
   * also sets sync registry version used at the moment of function registration.
   *
   * @param operatorTable drill operator table
   */
  public void register(DrillOperatorTable operatorTable) {
    AtomicInteger versionHolder = new AtomicInteger();
    final Map<String, Collection<DrillFuncHolder>> registeredFunctions =
        registryHolder.getAllFunctionsWithHolders(versionHolder).asMap();
    operatorTable.setFunctionRegistryVersion(versionHolder.get());
    registerOperatorsWithInference(operatorTable, registeredFunctions);
    registerOperatorsWithoutInference(operatorTable, registeredFunctions);
  }

  private void registerOperatorsWithInference(DrillOperatorTable operatorTable, Map<String,
      Collection<DrillFuncHolder>> registeredFunctions) {
    final Map<String, DrillSqlOperator.DrillSqlOperatorBuilder> map = Maps.newHashMap();
    final Map<String, DrillSqlAggOperator.DrillSqlAggOperatorBuilder> mapAgg = Maps.newHashMap();
    for (Entry<String, Collection<DrillFuncHolder>> function : registeredFunctions.entrySet()) {
      final ArrayListMultimap<Pair<Integer, Integer>, DrillFuncHolder> functions = ArrayListMultimap.create();
      final ArrayListMultimap<Integer, DrillFuncHolder> aggregateFunctions = ArrayListMultimap.create();
      final String name = function.getKey().toUpperCase();
      boolean isDeterministic = true;
      boolean isNiladic = false;
      for (DrillFuncHolder func : function.getValue()) {
        final int paramCount = func.getParamCount();
        if(func.isAggregating()) {
          aggregateFunctions.put(paramCount, func);
        } else {
          final Pair<Integer, Integer> argNumberRange;
          if(registeredFuncNameToArgRange.containsKey(name)) {
            argNumberRange = registeredFuncNameToArgRange.get(name);
          } else {
            argNumberRange = Pair.of(func.getParamCount(), func.getParamCount());
          }
          functions.put(argNumberRange, func);
        }

        if(!func.isDeterministic()) {
          isDeterministic = false;
        }

        if(func.isNiladic()) {
          isNiladic = true;
        }
      }
      for (Entry<Pair<Integer, Integer>, Collection<DrillFuncHolder>> entry : functions.asMap().entrySet()) {
        final Pair<Integer, Integer> range = entry.getKey();
        final int max = range.getRight();
        final int min = range.getLeft();
        if(!map.containsKey(name)) {
          map.put(name, new DrillSqlOperator.DrillSqlOperatorBuilder()
              .setName(name));
        }

        final DrillSqlOperator.DrillSqlOperatorBuilder drillSqlOperatorBuilder = map.get(name);
        drillSqlOperatorBuilder
            .addFunctions(entry.getValue())
            .setArgumentCount(min, max)
            .setDeterministic(isDeterministic)
            .setNiladic(isNiladic);
      }
      for (Entry<Integer, Collection<DrillFuncHolder>> entry : aggregateFunctions.asMap().entrySet()) {
        if(!mapAgg.containsKey(name)) {
          mapAgg.put(name, new DrillSqlAggOperator.DrillSqlAggOperatorBuilder().setName(name));
        }

        final DrillSqlAggOperator.DrillSqlAggOperatorBuilder drillSqlAggOperatorBuilder = mapAgg.get(name);
        drillSqlAggOperatorBuilder
            .addFunctions(entry.getValue())
            .setArgumentCount(entry.getKey(), entry.getKey());
      }
    }

    for(final Entry<String, DrillSqlOperator.DrillSqlOperatorBuilder> entry : map.entrySet()) {
      operatorTable.addOperatorWithInference(
          entry.getKey(),
          entry.getValue().build());
    }

    for(final Entry<String, DrillSqlAggOperator.DrillSqlAggOperatorBuilder> entry : mapAgg.entrySet()) {
      operatorTable.addOperatorWithInference(
          entry.getKey(),
          entry.getValue().build());
    }
  }

  private void registerOperatorsWithoutInference(DrillOperatorTable operatorTable, Map<String, Collection<DrillFuncHolder>> registeredFunctions) {
    SqlOperator op;
    for (Entry<String, Collection<DrillFuncHolder>> function : registeredFunctions.entrySet()) {
      Set<Integer> argCounts = Sets.newHashSet();
      String name = function.getKey().toUpperCase();
      for (DrillFuncHolder func : function.getValue()) {
        if (argCounts.add(func.getParamCount())) {
          if (func.isAggregating()) {
            op = new DrillSqlAggOperatorWithoutInference(name, func.getParamCount());
          } else {
            boolean isDeterministic;
            // prevent Drill from folding constant functions with types that cannot be materialized
            // into literals
            if (DrillConstExecutor.NON_REDUCIBLE_TYPES.contains(func.getReturnType().getMinorType())) {
              isDeterministic = false;
            } else {
              isDeterministic = func.isDeterministic();
            }
            op = new DrillSqlOperatorWithoutInference(name, func.getParamCount(), func.getReturnType(), isDeterministic, func.isNiladic());
          }
          operatorTable.addOperatorWithoutInference(function.getKey(), op);
        }
      }
    }
  }
}
