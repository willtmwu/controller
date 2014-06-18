/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.controller.cluster.datastore.messages;

import org.opendaylight.yangtools.yang.data.api.InstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;

public abstract class ModifyData {
  private final InstanceIdentifier path;
  private final NormalizedNode<?,?> data;

  public ModifyData(InstanceIdentifier path, NormalizedNode<?, ?> data) {
    this.path = path;
    this.data = data;
  }

  public InstanceIdentifier getPath() {
    return path;
  }

  public NormalizedNode<?, ?> getData() {
    return data;
  }

}
