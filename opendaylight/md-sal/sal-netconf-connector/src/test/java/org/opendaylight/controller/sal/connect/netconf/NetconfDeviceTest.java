/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.controller.sal.connect.netconf;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.junit.Test;
import org.mockito.Mockito;
import org.opendaylight.controller.netconf.api.NetconfMessage;
import org.opendaylight.controller.netconf.util.xml.XmlNetconfConstants;
import org.opendaylight.controller.sal.common.util.Rpcs;
import org.opendaylight.controller.sal.connect.api.MessageTransformer;
import org.opendaylight.controller.sal.connect.api.RemoteDeviceCommunicator;
import org.opendaylight.controller.sal.connect.api.RemoteDeviceHandler;
import org.opendaylight.controller.sal.connect.api.SchemaContextProviderFactory;
import org.opendaylight.controller.sal.connect.api.SchemaSourceProviderFactory;
import org.opendaylight.controller.sal.connect.netconf.listener.NetconfSessionCapabilities;
import org.opendaylight.controller.sal.connect.netconf.util.NetconfMessageTransformUtil;
import org.opendaylight.controller.sal.connect.util.RemoteDeviceId;
import org.opendaylight.controller.sal.core.api.RpcImplementation;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.RpcError;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.yangtools.yang.data.api.CompositeNode;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.model.api.SchemaContextProvider;
import org.opendaylight.yangtools.yang.model.util.repo.SchemaSourceProvider;
import org.opendaylight.yangtools.yang.parser.impl.YangParserImpl;

import com.google.common.base.Optional;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.Futures;

public class NetconfDeviceTest {

    private static final NetconfMessage netconfMessage;
    private static final CompositeNode compositeNode;

    static {
        try {
            netconfMessage = mockClass(NetconfMessage.class);
            compositeNode = mockClass(CompositeNode.class);
        } catch (final Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static final  RpcResult<NetconfMessage> rpcResult = Rpcs.getRpcResult(true, netconfMessage, Collections.<RpcError>emptySet());
    private static final  RpcResult<CompositeNode> rpcResultC = Rpcs.getRpcResult(true, compositeNode, Collections.<RpcError>emptySet());

    public static final String TEST_NAMESPACE = "test:namespace";
    public static final String TEST_MODULE = "test-module";
    public static final String TEST_REVISION = "2013-07-22";

    @Test
    public void testNetconfDeviceWithoutMonitoring() throws Exception {
        final RemoteDeviceHandler<NetconfSessionCapabilities> facade = getFacade();
        final RemoteDeviceCommunicator<NetconfMessage> listener = getListener();

        final NetconfDevice device = new NetconfDevice(getId(), facade, getExecutor(), getMessageTransformer(), getSchemaContextProviderFactory(), getSourceProviderFactory());
        device.onRemoteSessionUp(getSessionCaps(false, Collections.<String>emptyList()), listener);

        Mockito.verify(facade, Mockito.timeout(5000)).onDeviceDisconnected();
    }

    @Test
    public void testNetconfDeviceReconnect() throws Exception {
        final RemoteDeviceHandler<NetconfSessionCapabilities> facade = getFacade();
        final RemoteDeviceCommunicator<NetconfMessage> listener = getListener();

        final SchemaContextProviderFactory schemaContextProviderFactory = getSchemaContextProviderFactory();
        final SchemaSourceProviderFactory<InputStream> sourceProviderFactory = getSourceProviderFactory();
        final MessageTransformer<NetconfMessage> messageTransformer = getMessageTransformer();

        final NetconfDevice device = new NetconfDevice(getId(), facade, getExecutor(), messageTransformer, schemaContextProviderFactory, sourceProviderFactory);
        final NetconfSessionCapabilities sessionCaps = getSessionCaps(true,
                Lists.newArrayList(TEST_NAMESPACE + "?module=" + TEST_MODULE + "&amp;revision=" + TEST_REVISION));
        device.onRemoteSessionUp(sessionCaps, listener);

        verify(sourceProviderFactory, timeout(5000)).createSourceProvider(any(RpcImplementation.class));
        verify(schemaContextProviderFactory, timeout(5000)).createContextProvider(any(Collection.class), any(SchemaSourceProvider.class));
        verify(messageTransformer, timeout(5000)).onGlobalContextUpdated(any(SchemaContext.class));
        verify(facade, timeout(5000)).onDeviceConnected(any(SchemaContextProvider.class), any(NetconfSessionCapabilities.class), any(RpcImplementation.class));

        device.onRemoteSessionDown();
        verify(facade, timeout(5000)).onDeviceDisconnected();

        device.onRemoteSessionUp(sessionCaps, listener);

        verify(sourceProviderFactory, timeout(5000).times(2)).createSourceProvider(any(RpcImplementation.class));
        verify(schemaContextProviderFactory, timeout(5000).times(2)).createContextProvider(any(Collection.class), any(SchemaSourceProvider.class));
        verify(messageTransformer, timeout(5000).times(2)).onGlobalContextUpdated(any(SchemaContext.class));
        verify(facade, timeout(5000).times(2)).onDeviceConnected(any(SchemaContextProvider.class), any(NetconfSessionCapabilities.class), any(RpcImplementation.class));
    }

    private SchemaContextProviderFactory getSchemaContextProviderFactory() {
        final SchemaContextProviderFactory schemaContextProviderFactory = mockClass(SchemaContextProviderFactory.class);
        doReturn(new SchemaContextProvider() {
            @Override
            public SchemaContext getSchemaContext() {
                return getSchema();
            }
        }).when(schemaContextProviderFactory).createContextProvider(any(Collection.class), any(SchemaSourceProvider.class));
        return schemaContextProviderFactory;
    }

    public static SchemaContext getSchema() {
        final YangParserImpl parser = new YangParserImpl();
        final List<InputStream> modelsToParse = Lists.newArrayList(
                NetconfDeviceTest.class.getResourceAsStream("/schemas/test-module.yang")
        );
        final Set<Module> models = parser.parseYangModelsFromStreams(modelsToParse);
        return parser.resolveSchemaContext(models);
    }

    private RemoteDeviceHandler<NetconfSessionCapabilities> getFacade() throws Exception {
        final RemoteDeviceHandler<NetconfSessionCapabilities> remoteDeviceHandler = mockCloseableClass(RemoteDeviceHandler.class);
        doNothing().when(remoteDeviceHandler).onDeviceConnected(any(SchemaContextProvider.class), any(NetconfSessionCapabilities.class), any(RpcImplementation.class));
        doNothing().when(remoteDeviceHandler).onDeviceDisconnected();
        return remoteDeviceHandler;
    }

    private <T extends AutoCloseable> T mockCloseableClass(final Class<T> remoteDeviceHandlerClass) throws Exception {
        final T mock = mockClass(remoteDeviceHandlerClass);
        doNothing().when(mock).close();
        return mock;
    }

    public SchemaSourceProviderFactory<InputStream> getSourceProviderFactory() {
        final SchemaSourceProviderFactory<InputStream> mock = mockClass(SchemaSourceProviderFactory.class);

        final SchemaSourceProvider<InputStream> schemaSourceProvider = mockClass(SchemaSourceProvider.class);
        doReturn(Optional.<String>absent()).when(schemaSourceProvider).getSchemaSource(anyString(), any(Optional.class));

        doReturn(schemaSourceProvider).when(mock).createSourceProvider(any(RpcImplementation.class));
        return mock;
    }

    private static <T> T mockClass(final Class<T> remoteDeviceHandlerClass) {
        final T mock = Mockito.mock(remoteDeviceHandlerClass);
        Mockito.doReturn(remoteDeviceHandlerClass.getSimpleName()).when(mock).toString();
        return mock;
    }

    public RemoteDeviceId getId() {
        return new RemoteDeviceId("test-D");
    }

    public ExecutorService getExecutor() {
        return Executors.newSingleThreadExecutor();
    }

    public MessageTransformer<NetconfMessage> getMessageTransformer() throws Exception {
        final MessageTransformer<NetconfMessage> messageTransformer = mockClass(MessageTransformer.class);
        doReturn(netconfMessage).when(messageTransformer).toRpcRequest(any(QName.class), any(CompositeNode.class));
        doReturn(rpcResultC).when(messageTransformer).toRpcResult(any(NetconfMessage.class), any(QName.class));
        doNothing().when(messageTransformer).onGlobalContextUpdated(any(SchemaContext.class));
        return messageTransformer;
    }

    public NetconfSessionCapabilities getSessionCaps(final boolean addMonitor, final Collection<String> additionalCapabilities) {
        final ArrayList<String> capabilities = Lists.newArrayList(
                XmlNetconfConstants.URN_IETF_PARAMS_NETCONF_BASE_1_0,
                XmlNetconfConstants.URN_IETF_PARAMS_NETCONF_BASE_1_1);

        if(addMonitor) {
            capabilities.add(NetconfMessageTransformUtil.IETF_NETCONF_MONITORING.getNamespace().toString());
        }

        capabilities.addAll(additionalCapabilities);

        return NetconfSessionCapabilities.fromStrings(
                capabilities);
    }

    public RemoteDeviceCommunicator<NetconfMessage> getListener() throws Exception {
        final RemoteDeviceCommunicator<NetconfMessage> remoteDeviceCommunicator = mockCloseableClass(RemoteDeviceCommunicator.class);
        doReturn(Futures.immediateFuture(rpcResult)).when(remoteDeviceCommunicator).sendRequest(any(NetconfMessage.class), any(QName.class));
        return remoteDeviceCommunicator;
    }
}