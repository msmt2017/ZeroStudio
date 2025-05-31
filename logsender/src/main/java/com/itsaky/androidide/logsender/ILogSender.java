/*
 * This file is auto-generated.  DO NOT MODIFY.
 */
package com.itsaky.androidide.logsender;
/** The LogSender interface. */
public interface ILogSender extends android.os.IInterface
{
  /** Default implementation for ILogSender. */
  public static class Default implements com.itsaky.androidide.logsender.ILogSender
  {
    /** Called by the log receiver to check if the sender is alive. */
    @Override public void ping() throws android.os.RemoteException
    {
    }
    /** Asks the sender to start reading the logs. */
    @Override public void startReader(int port) throws android.os.RemoteException
    {
    }
    /** Get the process ID. */
    @Override public int getPid() throws android.os.RemoteException
    {
      return 0;
    }
    /** Get the package name of the sender. */
    @Override public java.lang.String getPackageName() throws android.os.RemoteException
    {
      return null;
    }
    /** Get the unique ID for this log sender. */
    @Override public java.lang.String getId() throws android.os.RemoteException
    {
      return null;
    }
    /** Called by the IDE to notify a client that the log receiver has been disconnected. */
    @Override public void onDisconnect() throws android.os.RemoteException
    {
    }
    @Override
    public android.os.IBinder asBinder() {
      return null;
    }
  }
  /** Local-side IPC implementation stub class. */
  public static abstract class Stub extends android.os.Binder implements com.itsaky.androidide.logsender.ILogSender
  {
    /** Construct the stub at attach it to the interface. */
    public Stub()
    {
      this.attachInterface(this, DESCRIPTOR);
    }
    /**
     * Cast an IBinder object into an com.itsaky.androidide.logsender.ILogSender interface,
     * generating a proxy if needed.
     */
    public static com.itsaky.androidide.logsender.ILogSender asInterface(android.os.IBinder obj)
    {
      if ((obj==null)) {
        return null;
      }
      android.os.IInterface iin = obj.queryLocalInterface(DESCRIPTOR);
      if (((iin!=null)&&(iin instanceof com.itsaky.androidide.logsender.ILogSender))) {
        return ((com.itsaky.androidide.logsender.ILogSender)iin);
      }
      return new com.itsaky.androidide.logsender.ILogSender.Stub.Proxy(obj);
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
          reply.writeNoException();
          break;
        }
        case TRANSACTION_startReader:
        {
          int _arg0;
          _arg0 = data.readInt();
          this.startReader(_arg0);
          reply.writeNoException();
          break;
        }
        case TRANSACTION_getPid:
        {
          int _result = this.getPid();
          reply.writeNoException();
          reply.writeInt(_result);
          break;
        }
        case TRANSACTION_getPackageName:
        {
          java.lang.String _result = this.getPackageName();
          reply.writeNoException();
          reply.writeString(_result);
          break;
        }
        case TRANSACTION_getId:
        {
          java.lang.String _result = this.getId();
          reply.writeNoException();
          reply.writeString(_result);
          break;
        }
        case TRANSACTION_onDisconnect:
        {
          this.onDisconnect();
          reply.writeNoException();
          break;
        }
        default:
        {
          return super.onTransact(code, data, reply, flags);
        }
      }
      return true;
    }
    private static class Proxy implements com.itsaky.androidide.logsender.ILogSender
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
      /** Called by the log receiver to check if the sender is alive. */
      @Override public void ping() throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          boolean _status = mRemote.transact(Stub.TRANSACTION_ping, _data, _reply, 0);
          _reply.readException();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
      }
      /** Asks the sender to start reading the logs. */
      @Override public void startReader(int port) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeInt(port);
          boolean _status = mRemote.transact(Stub.TRANSACTION_startReader, _data, _reply, 0);
          _reply.readException();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
      }
      /** Get the process ID. */
      @Override public int getPid() throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        int _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          boolean _status = mRemote.transact(Stub.TRANSACTION_getPid, _data, _reply, 0);
          _reply.readException();
          _result = _reply.readInt();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }
      /** Get the package name of the sender. */
      @Override public java.lang.String getPackageName() throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        java.lang.String _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          boolean _status = mRemote.transact(Stub.TRANSACTION_getPackageName, _data, _reply, 0);
          _reply.readException();
          _result = _reply.readString();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }
      /** Get the unique ID for this log sender. */
      @Override public java.lang.String getId() throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        java.lang.String _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          boolean _status = mRemote.transact(Stub.TRANSACTION_getId, _data, _reply, 0);
          _reply.readException();
          _result = _reply.readString();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }
      /** Called by the IDE to notify a client that the log receiver has been disconnected. */
      @Override public void onDisconnect() throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          boolean _status = mRemote.transact(Stub.TRANSACTION_onDisconnect, _data, _reply, 0);
          _reply.readException();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
      }
    }
    static final int TRANSACTION_ping = (android.os.IBinder.FIRST_CALL_TRANSACTION + 0);
    static final int TRANSACTION_startReader = (android.os.IBinder.FIRST_CALL_TRANSACTION + 1);
    static final int TRANSACTION_getPid = (android.os.IBinder.FIRST_CALL_TRANSACTION + 2);
    static final int TRANSACTION_getPackageName = (android.os.IBinder.FIRST_CALL_TRANSACTION + 3);
    static final int TRANSACTION_getId = (android.os.IBinder.FIRST_CALL_TRANSACTION + 4);
    static final int TRANSACTION_onDisconnect = (android.os.IBinder.FIRST_CALL_TRANSACTION + 5);
  }
  public static final java.lang.String DESCRIPTOR = "com.itsaky.androidide.logsender.ILogSender";
  /** Called by the log receiver to check if the sender is alive. */
  public void ping() throws android.os.RemoteException;
  /** Asks the sender to start reading the logs. */
  public void startReader(int port) throws android.os.RemoteException;
  /** Get the process ID. */
  public int getPid() throws android.os.RemoteException;
  /** Get the package name of the sender. */
  public java.lang.String getPackageName() throws android.os.RemoteException;
  /** Get the unique ID for this log sender. */
  public java.lang.String getId() throws android.os.RemoteException;
  /** Called by the IDE to notify a client that the log receiver has been disconnected. */
  public void onDisconnect() throws android.os.RemoteException;
}
