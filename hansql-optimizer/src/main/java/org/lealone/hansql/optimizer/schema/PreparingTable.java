package org.lealone.hansql.optimizer.schema;

import org.lealone.hansql.optimizer.plan.RelOptTable;
import org.lealone.hansql.optimizer.sql.validate.SqlValidatorTable;

/** Definition of a table, for the purposes of the validator and planner. */
  public interface PreparingTable
      extends RelOptTable, SqlValidatorTable {
  }