#!/bin/sh

IFACE=sit64o4
# try to automagically figure out our IPv4 default route
EXTIF=$(ip route show | egrep "^default" | sed 's/.* dev \(\w*\).*/\1/')
# try to use the non-secondary address if we have multiple
EXTIP=`ip -4 addr list $EXTIF | grep inet | grep -v secondary | awk '{ print $2'} | cut -d'/' -f1`
# now can compute the corresponding 6to4 prefix
PREFIX=$(printf "2002:%02x%02x:%02x%02x" $(echo ${EXTIP} | tr '.' ' '))

ip tunnel del $IFACE
ip tunnel add $IFACE mode sit remote any local $EXTIP ttl 255
ip link set dev $IFACE up
# if not explicitly configured, set default MTU
ip link set $IFACE mtu 1430
# Experienced IPv6 users will wonder why the netmask for sit0 is /16, not /64; 
# by setting the netmask to /16, you instruct your system to send packets 
# directly to the IPv4 address of other 6to4 users; if it was /64, you'd send 
# packets via the nearest relay router, increasing latency.
ip -6 addr add $PREFIX::/16 dev $IFACE
ip -6 route add 0:0:0:0:0:ffff::/96 dev $IFACE metric 1
ip -6 route add 2000::/3 via ::192.88.99.1 dev $IFACE metric 1
# for internal interfaces
#ip -6 addr add $PREFIX:${intifprefix}::/64 dev $intif
