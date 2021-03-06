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
package org.lealone.hansql.exec.store;

import java.io.IOException;
import java.util.List;

import org.apache.drill.shaded.guava.com.google.common.collect.Lists;
import org.lealone.hansql.common.AutoCloseables;
import org.lealone.hansql.common.exceptions.UserException;
import org.lealone.hansql.exec.ExecConstants;
import org.lealone.hansql.exec.context.DrillbitContext;
import org.lealone.hansql.exec.context.options.OptionManager;
import org.lealone.hansql.exec.context.options.OptionValue;
import org.lealone.hansql.exec.ops.ViewExpansionContext;
import org.lealone.hansql.exec.store.SchemaConfig.SchemaConfigInfoProvider;
import org.lealone.hansql.exec.util.ImpersonationUtil;
import org.lealone.hansql.optimizer.schema.SchemaPlus;

/**
 * Class which creates new schema trees. It keeps track of newly created schema trees and closes them safely as
 * part of {@link #close()}.
 */
public class SchemaTreeProvider implements AutoCloseable {
  private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(SchemaTreeProvider.class);

  private final DrillbitContext dContext;
  private final List<SchemaPlus> schemaTreesToClose;
  private final boolean isImpersonationEnabled;

  public SchemaTreeProvider(final DrillbitContext dContext) {
    this.dContext = dContext;
    schemaTreesToClose = Lists.newArrayList();
    isImpersonationEnabled = dContext.getConfig().getBoolean(ExecConstants.IMPERSONATION_ENABLED);
  }

  /**
   * Return root schema for process user.
   *
   * @param options list of options
   * @return root of the schema tree
   */
  public SchemaPlus createRootSchema(final OptionManager options) {
    SchemaConfigInfoProvider schemaConfigInfoProvider = new SchemaConfigInfoProvider() {

      @Override
      public ViewExpansionContext getViewExpansionContext() {
        throw new UnsupportedOperationException("View expansion context is not supported");
      }

      @Override
      public OptionValue getOption(String optionKey) {
        return options.getOption(optionKey);
      }

      @Override public SchemaPlus getRootSchema(String userName) {
        return createRootSchema(userName, this);
      }

      @Override public String getQueryUserName() {
        return ImpersonationUtil.getProcessUserName();
      }
    };

    final SchemaConfig schemaConfig = SchemaConfig.newBuilder(
        ImpersonationUtil.getProcessUserName(), schemaConfigInfoProvider)
        .build();

    return createRootSchema(schemaConfig);
  }

  /**
   * Return root schema with schema owner as the given user.
   *
   * @param userName Name of the user who is accessing the storage sources.
   * @param provider {@link SchemaConfigInfoProvider} instance
   * @return Root of the schema tree.
   */
  public SchemaPlus createRootSchema(final String userName, final SchemaConfigInfoProvider provider) {
    final String schemaUser = isImpersonationEnabled ? userName : ImpersonationUtil.getProcessUserName();
    final SchemaConfig schemaConfig = SchemaConfig.newBuilder(schemaUser, provider).build();
    return createRootSchema(schemaConfig);
  }

  /**
   * Create and return a SchemaTree with given <i>schemaConfig</i>.
   * @param schemaConfig
   * @return
   */
  public SchemaPlus createRootSchema(SchemaConfig schemaConfig) {
      final SchemaPlus rootSchema = DynamicSchema.createRootSchema(dContext.getStorage(), schemaConfig);
      schemaTreesToClose.add(rootSchema);
      return rootSchema;
  }

  /**
   * Return full root schema with schema owner as the given user.
   *
   * @param userName Name of the user who is accessing the storage sources.
   * @param provider {@link SchemaConfigInfoProvider} instance
   * @return Root of the schema tree.
   */
  public SchemaPlus createFullRootSchema(final String userName, final SchemaConfigInfoProvider provider) {
    final String schemaUser = isImpersonationEnabled ? userName : ImpersonationUtil.getProcessUserName();
    final SchemaConfig schemaConfig = SchemaConfig.newBuilder(schemaUser, provider).build();
    return createFullRootSchema(schemaConfig);
  }
  /**
   * Create and return a Full SchemaTree with given <i>schemaConfig</i>.
   * @param schemaConfig
   * @return
   */
  public SchemaPlus createFullRootSchema(SchemaConfig schemaConfig) {
    try {
      final SchemaPlus rootSchema = DynamicSchema.createRootSchema(dContext.getStorage(), schemaConfig);
      dContext.getSchemaFactory().registerSchemas(schemaConfig, rootSchema);
      schemaTreesToClose.add(rootSchema);
      return rootSchema;
    }
    catch(IOException e) {
      // We can't proceed further without a schema, throw a runtime exception.
      // Improve the error message for client side.

      final String contextString = isImpersonationEnabled ? "[Hint: Username is absent in connection URL or doesn't " +
          "exist on Drillbit node. Please specify a username in connection URL which is present on Drillbit node.]" :
          "";
      throw UserException
          .resourceError(e)
          .message("Failed to create schema tree.")
          .addContext("IOException: ", e.getMessage())
          .addContext(contextString)
          .build(logger);
    }

  }

  @Override
  public void close() throws Exception {
    List<AutoCloseable> toClose = Lists.newArrayList();
    for(SchemaPlus tree : schemaTreesToClose) {
      addSchemasToCloseList(tree, toClose);
    }

    AutoCloseables.close(toClose);
  }

  private static void addSchemasToCloseList(final SchemaPlus tree, final List<AutoCloseable> toClose) {
    for(String subSchemaName : tree.getSubSchemaNames()) {
      addSchemasToCloseList(tree.getSubSchema(subSchemaName), toClose);
    }

    try {
      AbstractSchema drillSchemaImpl =  tree.unwrap(AbstractSchema.class);
      toClose.add(drillSchemaImpl);
    } catch (ClassCastException e) {
      // Ignore as the SchemaPlus is not an implementation of Drill schema.
    }
  }
}
