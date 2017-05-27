package edu.mdc.csclub.ibeaconsurveying;


/**
 * Created by nelly on 5/23/17.
 */

public class iBeacon {
    private String uuid;
    private int major;
    private int minor;

    public iBeacon(String uuid, int major, int minor) {
        this.uuid = uuid;
        this.major = major;
        this.minor = minor;
    }

    public String getUuid() {
        return uuid;
    }

    public int getMajor() {
        return major;
    }

    public int getMinor() {
        return minor;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        iBeacon iBeacon = (iBeacon) o;

        if (major != iBeacon.major) return false;
        if (minor != iBeacon.minor) return false;
        return uuid != null ? uuid.equalsIgnoreCase(iBeacon.uuid) : iBeacon.uuid == null;

    }

    @Override
    public int hashCode() {
        int result = uuid != null ? uuid.hashCode() : 0;
        result = 31 * result + major;
        result = 31 * result + minor;
        return result;
    }

    @Override
    public String toString() {
        return "iBeacon{" +
                "uuid='" + uuid + '\'' +
                ", major=" + major +
                ", minor=" + minor +
                '}';
    }
}
