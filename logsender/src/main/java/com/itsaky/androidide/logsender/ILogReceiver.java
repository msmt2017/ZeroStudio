/*
 * This file is auto-generated.  DO NOT MODIFY.
 */
package com.itsaky.androidide.logsender;
/** The LogReceiver interface. */
public interface ILogReceiver extends android.os.IInterface
{
  /** Default implementation for ILogReceiver. */
  public static class Default implements com.itsaky.androidide.logsender.ILogReceiver
  {
    /** Called by the log sender to check if the receiver is alive. */
    @Override public void ping() throws android.os.RemoteException
    {
    }
    /** Called by the client applications to connect to log receiver in AndroidIDE. */
    @Override public void connect(com.itsaky.androidide.logsender.ILogSender sender) throws android.os.RemoteException
    {
    }
    /** Called by the client applications to disconnect from the log receiver in AndroidIDE. */
    @Override public void disconnect(java.lang.String packageName, java.lang.String senderId) throws android.os.RemoteException
    {
    }
    @Override
    public android.os.IBinder asBinder() {
      return null;
    }
  }
  /** Local-side IPC implementation stub class. */
  public static abstract class Stub extends android.os.Binder implements com.itsaky.androidide.logsender.ILogReceiver
  {
    /** Construct the stub at attach it to the interface. */
    public Stub()
    {
      this.attachInterface(this, DESCRIPTOR);
    }
    /**
     * Cast an IBinder object into an com.itsaky.androidide.logsender.ILogReceiver interface,
     * generating a proxy if needed.
     */
    public static com.itsaky.androidide.logsender.ILogReceiver asInterface(android.os.IBinder obj)
    {
      if ((obj==null)) {
        return null;
      }
      android.os.IInterface iin = obj.queryLocalInterface(DESCRIPTOR);
      if (((iin!=null)&&(iin instanceof com.itsaky.androidide.logsender.ILogReceiver))) {
        return ((com.itsaky.androidide.logsender.ILogReceiver)iin);
      }
      return new com.itsaky.androidide.logsender.ILogReceiver.Stub.Proxy(obj);
    }
    @Override public android.os.IBinder asBinder()
    {
      return this;
    }
    @Override public boolean onTransact(int code, android.os.Parcel data, android.os.Parcel reply, int flags) throws android.os.RemoteException
    {
      java.lang.String descriptor = DESCRIPTOR;
      if (code >= android.os.IBinder.FIRST_CALL_TRANSACTION && code <= android.os.IBinder.LAST_CALL_TRANSACTION) {
        data.enforceInterface(descriptor);
      }
      switch (code)
      {
        case INTERFACE_TRANSACTION:
        {
          reply.writeString(descriptor);
          return true;
        }
      }
      switch (code)
      {
        case TRANSACTION_ping:
        {
          this.ping();
          break;
        }
        case TRANSACTION_connect:
        {
          com.itsaky.androidide.logsender.ILogSender _arg0;
          _arg0 = com.itsaky.androidide.logsender.ILogSender.Stub.asInterface(data.readStrongBinder());
          this.connect(_arg0);
          break;
        }
        case TRANSACTION_disconnect:
        {
          java.lang.String _arg0;
          _arg0 = data.readString();
          java.lang.String _arg1;
          _arg1 = data.readString();
          this.disconnect(_arg0, _arg1);
          break;
        }
        default:
        {
          return super.onTransact(code, data, reply, flags);
        }
      }
      return true;
    }
    private static class Proxy implements com.itsaky.androidide.logsender.ILogReceiver
    {
      private android.os.IBinder mRemote;
      Proxy(android.os.IBinder remote)
      {
        mRemote = remote;
      }
      @Override public android.os.IBinder asBinder()
      {
        return mRemote;
      }
      public java.lang.String getInterfaceDescriptor()
      {
        return DESCRIPTOR;
      }
      /** Called by the log sender to check if the receiver is alive. */
      @Override public void ping() throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          boolean _status = mRemote.transact(Stub.TRANSACTION_ping, _data, null, android.os.IBinder.FLAG_ONEWAY);
        }
        finally {
          _data.recycle();
        }
      }
      /** Called by the client applications to connect to log receiver in AndroidIDE. */
      @Override public void connect(com.itsaky.androidide.logsender.ILogSender sender) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeStrongInterface(sender);
          boolean _status = mRemote.transact(Stub.TRANSACTION_connect, _data, null, android.os.IBinder.FLAG_ONEWAY);
        }
        finally {
          _data.recycle();
        }
      }
      /** Called by the client applications to disconnect from the log receiver in AndroidIDE. */
      @Override public void disconnect(java.lang.String packageName, java.lang.String senderId) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeString(packageName);
          _data.writeString(senderId);
          boolean _status = mRemote.transact(Stub.TRANSACTION_disconnect, _data, null, android.os.IBinder.FLAG_ONEWAY);
        }
        finally {
          _data.recycle();
        }
      }
    }
    static final int TRANSACTION_ping = (android.os.IBinder.FIRST_CALL_TRANSACTION + 0);
    static final int TRANSACTION_connect = (android.os.IBinder.FIRST_CALL_TRANSACTION + 1);
    static final int TRANSACTION_disconnect = (android.os.IBinder.FIRST_CALL_TRANSACTION + 2);
  }
  public static final java.lang.String DESCRIPTOR = "com.itsaky.androidide.logsender.ILogReceiver";
  /** Called by the log sender to check if the receiver is alive. */
  public void ping() throws android.os.RemoteException;
  /** Called by the client applications to connect to log receiver in AndroidIDE. */
  public void connect(com.itsaky.androidide.logsender.ILogSender sender) throws android.os.RemoteException;
  /** Called by the client applications to disconnect from the log receiver in AndroidIDE. */
  public void disconnect(java.lang.String packageName, java.lang.String senderId) throws android.os.RemoteException;
}
