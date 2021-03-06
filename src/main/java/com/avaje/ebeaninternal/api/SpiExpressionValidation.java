package com.avaje.ebeaninternal.api;

import com.avaje.ebean.plugin.SpiBeanType;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Property expression validation request for a given root bean type.
 */
public class SpiExpressionValidation {

  private final SpiBeanType<?> desc;

  private final LinkedHashSet<String> unknown = new LinkedHashSet<String>();

  public SpiExpressionValidation(SpiBeanType<?> desc) {
    this.desc = desc;
  }

  /**
   * Validate that the property expression (path) is valid.
   */
  public void validate(String propertyName) {
    if (!desc.isValidExpression(propertyName)) {
      unknown.add(propertyName);
    }
  }

  /**
   * Return the set of properties considered as having unknown paths.
   */
  public Set<String> getUnknownProperties() {
    return unknown;
  }

}
