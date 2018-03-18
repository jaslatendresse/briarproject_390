package org.briarproject.bramble.plugin.tcp;

import org.briarproject.bramble.api.nullsafety.MethodsNotNullByDefault;
import org.briarproject.bramble.api.nullsafety.ParametersNotNullByDefault;
import org.briarproject.bramble.api.plugin.Backoff;
import org.briarproject.bramble.api.plugin.TransportId;
import org.briarproject.bramble.api.plugin.duplex.DuplexPluginCallback;
import org.briarproject.bramble.api.properties.TransportProperties;
import org.briarproject.bramble.restClient.BServerServices;
import org.briarproject.bramble.restClient.BServerServicesImpl;
import org.h2.util.New;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Executor;

import static org.briarproject.bramble.api.plugin.WanTcpConstants.ID;

/**
 * Created by Winterhart on 2/25/2018.
 * This class is similar to a WLAN Driver
 * However, it contains "hack" to connect to another user over the internet using our server
 */
@MethodsNotNullByDefault
@ParametersNotNullByDefault
public class CustomWanTcpPlugin extends TcpPlugin {

    private static final String PROP_IP_PORT = "ipPort";

    private final PortMapper portMapper;

    private volatile MappingResult mappingResult;

    CustomWanTcpPlugin(Executor ioExecutor, Backoff backoff, PortMapper portMapper,
                       DuplexPluginCallback callback, int maxLatency, int maxIdleTime) {
        super(ioExecutor, backoff, callback, maxLatency, maxIdleTime);
        this.portMapper = portMapper;
    }

    @Override
    public TransportId getId() {
        return ID;
    }

    @Override
    protected List<InetSocketAddress> getLocalSocketAddresses() {
        // Use the same address and port as last time if available
        TransportProperties p = callback.getLocalProperties();
        InetSocketAddress old = injectSocketAddressFromServer(p.get(PROP_IP_PORT), "UNIQUEID");
        List<InetSocketAddress> addrs = new LinkedList<>();
        for (InetAddress a : getLocalIpAddresses()) {
            if (isAcceptableAddress(a)) {
                // If this is the old address, try to use the same port
                if (old != null && old.getAddress().equals(a))
                    addrs.add(0, new InetSocketAddress(a, old.getPort()));
                addrs.add(new InetSocketAddress(a, 0));
            }
        }
        // Accept interfaces with local addresses that can be port-mapped
        int port = old == null ? chooseEphemeralPort() : old.getPort();
        mappingResult = portMapper.map(port);
        if (mappingResult != null && mappingResult.isUsable()) {
            InetSocketAddress a = mappingResult.getInternal();
            if (a != null && a.getAddress() instanceof Inet4Address)
                addrs.add(a);
        }
        return addrs;
    }

    private boolean isAcceptableAddress(InetAddress a) {
        // Accept global IPv4 addresses
        boolean ipv4 = a instanceof Inet4Address;
        boolean loop = a.isLoopbackAddress();
        boolean link = a.isLinkLocalAddress();
        boolean site = a.isSiteLocalAddress();
        return ipv4 && !loop && !link && !site;
    }

    /**
     * Let's change this method so each time it is called, the saved port on our server will be update...
     * @return Port Choosen
     */
    private int chooseEphemeralPort() {
        int NewPort = 32768 + (int) (Math.random() * 32768);
        // Send the new port to briar server
        udateDataOnBServer(NewPort);
        return NewPort;
    }

    @Override
    protected List<InetSocketAddress> getRemoteSocketAddresses(
            TransportProperties p) {
        InetSocketAddress parsed = injectSocketAddressFromServer(p.get(PROP_IP_PORT), "UNIQUEID");

        if (parsed == null) return Collections.emptyList();
        return Collections.singletonList(parsed);
    }

    @Override
    protected boolean isConnectable(InetSocketAddress remote) {
        if (remote.getPort() == 0) return false;
        return isAcceptableAddress(remote.getAddress());
    }

    @Override
    protected void setLocalSocketAddress(InetSocketAddress a) {
        if (mappingResult != null && mappingResult.isUsable()) {
            // Advertise the external address to contacts
            if (a.equals(mappingResult.getInternal())) {
                InetSocketAddress external = mappingResult.getExternal();
                if (external != null) a = external;
            }
        }
        TransportProperties p = new TransportProperties();
        p.put(PROP_IP_PORT, getIpPortString(a));
        callback.mergeLocalProperties(p);
    }



}