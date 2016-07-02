// Generated by the protocol buffer compiler.  DO NOT EDIT!
// source: ClusterClientMessages.proto

package akka.cluster.client.protobuf.msg;

public final class ClusterClientMessages {
  private ClusterClientMessages() {}
  public static void registerAllExtensions(
      akka.protobuf.ExtensionRegistry registry) {
  }
  public interface ContactsOrBuilder
      extends akka.protobuf.MessageOrBuilder {

    // repeated string contactPoints = 1;
    /**
     * <code>repeated string contactPoints = 1;</code>
     */
    java.util.List<java.lang.String>
    getContactPointsList();
    /**
     * <code>repeated string contactPoints = 1;</code>
     */
    int getContactPointsCount();
    /**
     * <code>repeated string contactPoints = 1;</code>
     */
    java.lang.String getContactPoints(int index);
    /**
     * <code>repeated string contactPoints = 1;</code>
     */
    akka.protobuf.ByteString
        getContactPointsBytes(int index);
  }
  /**
   * Protobuf type {@code Contacts}
   */
  public static final class Contacts extends
      akka.protobuf.GeneratedMessage
      implements ContactsOrBuilder {
    // Use Contacts.newBuilder() to construct.
    private Contacts(akka.protobuf.GeneratedMessage.Builder<?> builder) {
      super(builder);
      this.unknownFields = builder.getUnknownFields();
    }
    private Contacts(boolean noInit) { this.unknownFields = akka.protobuf.UnknownFieldSet.getDefaultInstance(); }

    private static final Contacts defaultInstance;
    public static Contacts getDefaultInstance() {
      return defaultInstance;
    }

    public Contacts getDefaultInstanceForType() {
      return defaultInstance;
    }

    private final akka.protobuf.UnknownFieldSet unknownFields;
    @java.lang.Override
    public final akka.protobuf.UnknownFieldSet
        getUnknownFields() {
      return this.unknownFields;
    }
    private Contacts(
        akka.protobuf.CodedInputStream input,
        akka.protobuf.ExtensionRegistryLite extensionRegistry)
        throws akka.protobuf.InvalidProtocolBufferException {
      initFields();
      int mutable_bitField0_ = 0;
      akka.protobuf.UnknownFieldSet.Builder unknownFields =
          akka.protobuf.UnknownFieldSet.newBuilder();
      try {
        boolean done = false;
        while (!done) {
          int tag = input.readTag();
          switch (tag) {
            case 0:
              done = true;
              break;
            default: {
              if (!parseUnknownField(input, unknownFields,
                                     extensionRegistry, tag)) {
                done = true;
              }
              break;
            }
            case 10: {
              if (!((mutable_bitField0_ & 0x00000001) == 0x00000001)) {
                contactPoints_ = new akka.protobuf.LazyStringArrayList();
                mutable_bitField0_ |= 0x00000001;
              }
              contactPoints_.add(input.readBytes());
              break;
            }
          }
        }
      } catch (akka.protobuf.InvalidProtocolBufferException e) {
        throw e.setUnfinishedMessage(this);
      } catch (java.io.IOException e) {
        throw new akka.protobuf.InvalidProtocolBufferException(
            e.getMessage()).setUnfinishedMessage(this);
      } finally {
        if (((mutable_bitField0_ & 0x00000001) == 0x00000001)) {
          contactPoints_ = new akka.protobuf.UnmodifiableLazyStringList(contactPoints_);
        }
        this.unknownFields = unknownFields.build();
        makeExtensionsImmutable();
      }
    }
    public static final akka.protobuf.Descriptors.Descriptor
        getDescriptor() {
      return akka.cluster.client.protobuf.msg.ClusterClientMessages.internal_static_Contacts_descriptor;
    }

    protected akka.protobuf.GeneratedMessage.FieldAccessorTable
        internalGetFieldAccessorTable() {
      return akka.cluster.client.protobuf.msg.ClusterClientMessages.internal_static_Contacts_fieldAccessorTable
          .ensureFieldAccessorsInitialized(
              akka.cluster.client.protobuf.msg.ClusterClientMessages.Contacts.class, akka.cluster.client.protobuf.msg.ClusterClientMessages.Contacts.Builder.class);
    }

    public static akka.protobuf.Parser<Contacts> PARSER =
        new akka.protobuf.AbstractParser<Contacts>() {
      public Contacts parsePartialFrom(
          akka.protobuf.CodedInputStream input,
          akka.protobuf.ExtensionRegistryLite extensionRegistry)
          throws akka.protobuf.InvalidProtocolBufferException {
        return new Contacts(input, extensionRegistry);
      }
    };

    @java.lang.Override
    public akka.protobuf.Parser<Contacts> getParserForType() {
      return PARSER;
    }

    // repeated string contactPoints = 1;
    public static final int CONTACTPOINTS_FIELD_NUMBER = 1;
    private akka.protobuf.LazyStringList contactPoints_;
    /**
     * <code>repeated string contactPoints = 1;</code>
     */
    public java.util.List<java.lang.String>
        getContactPointsList() {
      return contactPoints_;
    }
    /**
     * <code>repeated string contactPoints = 1;</code>
     */
    public int getContactPointsCount() {
      return contactPoints_.size();
    }
    /**
     * <code>repeated string contactPoints = 1;</code>
     */
    public java.lang.String getContactPoints(int index) {
      return contactPoints_.get(index);
    }
    /**
     * <code>repeated string contactPoints = 1;</code>
     */
    public akka.protobuf.ByteString
        getContactPointsBytes(int index) {
      return contactPoints_.getByteString(index);
    }

    private void initFields() {
      contactPoints_ = akka.protobuf.LazyStringArrayList.EMPTY;
    }
    private byte memoizedIsInitialized = -1;
    public final boolean isInitialized() {
      byte isInitialized = memoizedIsInitialized;
      if (isInitialized != -1) return isInitialized == 1;

      memoizedIsInitialized = 1;
      return true;
    }

    public void writeTo(akka.protobuf.CodedOutputStream output)
                        throws java.io.IOException {
      getSerializedSize();
      for (int i = 0; i < contactPoints_.size(); i++) {
        output.writeBytes(1, contactPoints_.getByteString(i));
      }
      getUnknownFields().writeTo(output);
    }

    private int memoizedSerializedSize = -1;
    public int getSerializedSize() {
      int size = memoizedSerializedSize;
      if (size != -1) return size;

      size = 0;
      {
        int dataSize = 0;
        for (int i = 0; i < contactPoints_.size(); i++) {
          dataSize += akka.protobuf.CodedOutputStream
            .computeBytesSizeNoTag(contactPoints_.getByteString(i));
        }
        size += dataSize;
        size += 1 * getContactPointsList().size();
      }
      size += getUnknownFields().getSerializedSize();
      memoizedSerializedSize = size;
      return size;
    }

    private static final long serialVersionUID = 0L;
    @java.lang.Override
    protected java.lang.Object writeReplace()
        throws java.io.ObjectStreamException {
      return super.writeReplace();
    }

    public static akka.cluster.client.protobuf.msg.ClusterClientMessages.Contacts parseFrom(
        akka.protobuf.ByteString data)
        throws akka.protobuf.InvalidProtocolBufferException {
      return PARSER.parseFrom(data);
    }
    public static akka.cluster.client.protobuf.msg.ClusterClientMessages.Contacts parseFrom(
        akka.protobuf.ByteString data,
        akka.protobuf.ExtensionRegistryLite extensionRegistry)
        throws akka.protobuf.InvalidProtocolBufferException {
      return PARSER.parseFrom(data, extensionRegistry);
    }
    public static akka.cluster.client.protobuf.msg.ClusterClientMessages.Contacts parseFrom(byte[] data)
        throws akka.protobuf.InvalidProtocolBufferException {
      return PARSER.parseFrom(data);
    }
    public static akka.cluster.client.protobuf.msg.ClusterClientMessages.Contacts parseFrom(
        byte[] data,
        akka.protobuf.ExtensionRegistryLite extensionRegistry)
        throws akka.protobuf.InvalidProtocolBufferException {
      return PARSER.parseFrom(data, extensionRegistry);
    }
    public static akka.cluster.client.protobuf.msg.ClusterClientMessages.Contacts parseFrom(java.io.InputStream input)
        throws java.io.IOException {
      return PARSER.parseFrom(input);
    }
    public static akka.cluster.client.protobuf.msg.ClusterClientMessages.Contacts parseFrom(
        java.io.InputStream input,
        akka.protobuf.ExtensionRegistryLite extensionRegistry)
        throws java.io.IOException {
      return PARSER.parseFrom(input, extensionRegistry);
    }
    public static akka.cluster.client.protobuf.msg.ClusterClientMessages.Contacts parseDelimitedFrom(java.io.InputStream input)
        throws java.io.IOException {
      return PARSER.parseDelimitedFrom(input);
    }
    public static akka.cluster.client.protobuf.msg.ClusterClientMessages.Contacts parseDelimitedFrom(
        java.io.InputStream input,
        akka.protobuf.ExtensionRegistryLite extensionRegistry)
        throws java.io.IOException {
      return PARSER.parseDelimitedFrom(input, extensionRegistry);
    }
    public static akka.cluster.client.protobuf.msg.ClusterClientMessages.Contacts parseFrom(
        akka.protobuf.CodedInputStream input)
        throws java.io.IOException {
      return PARSER.parseFrom(input);
    }
    public static akka.cluster.client.protobuf.msg.ClusterClientMessages.Contacts parseFrom(
        akka.protobuf.CodedInputStream input,
        akka.protobuf.ExtensionRegistryLite extensionRegistry)
        throws java.io.IOException {
      return PARSER.parseFrom(input, extensionRegistry);
    }

    public static Builder newBuilder() { return Builder.create(); }
    public Builder newBuilderForType() { return newBuilder(); }
    public static Builder newBuilder(akka.cluster.client.protobuf.msg.ClusterClientMessages.Contacts prototype) {
      return newBuilder().mergeFrom(prototype);
    }
    public Builder toBuilder() { return newBuilder(this); }

    @java.lang.Override
    protected Builder newBuilderForType(
        akka.protobuf.GeneratedMessage.BuilderParent parent) {
      Builder builder = new Builder(parent);
      return builder;
    }
    /**
     * Protobuf type {@code Contacts}
     */
    public static final class Builder extends
        akka.protobuf.GeneratedMessage.Builder<Builder>
       implements akka.cluster.client.protobuf.msg.ClusterClientMessages.ContactsOrBuilder {
      public static final akka.protobuf.Descriptors.Descriptor
          getDescriptor() {
        return akka.cluster.client.protobuf.msg.ClusterClientMessages.internal_static_Contacts_descriptor;
      }

      protected akka.protobuf.GeneratedMessage.FieldAccessorTable
          internalGetFieldAccessorTable() {
        return akka.cluster.client.protobuf.msg.ClusterClientMessages.internal_static_Contacts_fieldAccessorTable
            .ensureFieldAccessorsInitialized(
                akka.cluster.client.protobuf.msg.ClusterClientMessages.Contacts.class, akka.cluster.client.protobuf.msg.ClusterClientMessages.Contacts.Builder.class);
      }

      // Construct using akka.cluster.client.protobuf.msg.ClusterClientMessages.Contacts.newBuilder()
      private Builder() {
        maybeForceBuilderInitialization();
      }

      private Builder(
          akka.protobuf.GeneratedMessage.BuilderParent parent) {
        super(parent);
        maybeForceBuilderInitialization();
      }
      private void maybeForceBuilderInitialization() {
        if (akka.protobuf.GeneratedMessage.alwaysUseFieldBuilders) {
        }
      }
      private static Builder create() {
        return new Builder();
      }

      public Builder clear() {
        super.clear();
        contactPoints_ = akka.protobuf.LazyStringArrayList.EMPTY;
        bitField0_ = (bitField0_ & ~0x00000001);
        return this;
      }

      public Builder clone() {
        return create().mergeFrom(buildPartial());
      }

      public akka.protobuf.Descriptors.Descriptor
          getDescriptorForType() {
        return akka.cluster.client.protobuf.msg.ClusterClientMessages.internal_static_Contacts_descriptor;
      }

      public akka.cluster.client.protobuf.msg.ClusterClientMessages.Contacts getDefaultInstanceForType() {
        return akka.cluster.client.protobuf.msg.ClusterClientMessages.Contacts.getDefaultInstance();
      }

      public akka.cluster.client.protobuf.msg.ClusterClientMessages.Contacts build() {
        akka.cluster.client.protobuf.msg.ClusterClientMessages.Contacts result = buildPartial();
        if (!result.isInitialized()) {
          throw newUninitializedMessageException(result);
        }
        return result;
      }

      public akka.cluster.client.protobuf.msg.ClusterClientMessages.Contacts buildPartial() {
        akka.cluster.client.protobuf.msg.ClusterClientMessages.Contacts result = new akka.cluster.client.protobuf.msg.ClusterClientMessages.Contacts(this);
        int from_bitField0_ = bitField0_;
        if (((bitField0_ & 0x00000001) == 0x00000001)) {
          contactPoints_ = new akka.protobuf.UnmodifiableLazyStringList(
              contactPoints_);
          bitField0_ = (bitField0_ & ~0x00000001);
        }
        result.contactPoints_ = contactPoints_;
        onBuilt();
        return result;
      }

      public Builder mergeFrom(akka.protobuf.Message other) {
        if (other instanceof akka.cluster.client.protobuf.msg.ClusterClientMessages.Contacts) {
          return mergeFrom((akka.cluster.client.protobuf.msg.ClusterClientMessages.Contacts)other);
        } else {
          super.mergeFrom(other);
          return this;
        }
      }

      public Builder mergeFrom(akka.cluster.client.protobuf.msg.ClusterClientMessages.Contacts other) {
        if (other == akka.cluster.client.protobuf.msg.ClusterClientMessages.Contacts.getDefaultInstance()) return this;
        if (!other.contactPoints_.isEmpty()) {
          if (contactPoints_.isEmpty()) {
            contactPoints_ = other.contactPoints_;
            bitField0_ = (bitField0_ & ~0x00000001);
          } else {
            ensureContactPointsIsMutable();
            contactPoints_.addAll(other.contactPoints_);
          }
          onChanged();
        }
        this.mergeUnknownFields(other.getUnknownFields());
        return this;
      }

      public final boolean isInitialized() {
        return true;
      }

      public Builder mergeFrom(
          akka.protobuf.CodedInputStream input,
          akka.protobuf.ExtensionRegistryLite extensionRegistry)
          throws java.io.IOException {
        akka.cluster.client.protobuf.msg.ClusterClientMessages.Contacts parsedMessage = null;
        try {
          parsedMessage = PARSER.parsePartialFrom(input, extensionRegistry);
        } catch (akka.protobuf.InvalidProtocolBufferException e) {
          parsedMessage = (akka.cluster.client.protobuf.msg.ClusterClientMessages.Contacts) e.getUnfinishedMessage();
          throw e;
        } finally {
          if (parsedMessage != null) {
            mergeFrom(parsedMessage);
          }
        }
        return this;
      }
      private int bitField0_;

      // repeated string contactPoints = 1;
      private akka.protobuf.LazyStringList contactPoints_ = akka.protobuf.LazyStringArrayList.EMPTY;
      private void ensureContactPointsIsMutable() {
        if (!((bitField0_ & 0x00000001) == 0x00000001)) {
          contactPoints_ = new akka.protobuf.LazyStringArrayList(contactPoints_);
          bitField0_ |= 0x00000001;
         }
      }
      /**
       * <code>repeated string contactPoints = 1;</code>
       */
      public java.util.List<java.lang.String>
          getContactPointsList() {
        return java.util.Collections.unmodifiableList(contactPoints_);
      }
      /**
       * <code>repeated string contactPoints = 1;</code>
       */
      public int getContactPointsCount() {
        return contactPoints_.size();
      }
      /**
       * <code>repeated string contactPoints = 1;</code>
       */
      public java.lang.String getContactPoints(int index) {
        return contactPoints_.get(index);
      }
      /**
       * <code>repeated string contactPoints = 1;</code>
       */
      public akka.protobuf.ByteString
          getContactPointsBytes(int index) {
        return contactPoints_.getByteString(index);
      }
      /**
       * <code>repeated string contactPoints = 1;</code>
       */
      public Builder setContactPoints(
          int index, java.lang.String value) {
        if (value == null) {
    throw new NullPointerException();
  }
  ensureContactPointsIsMutable();
        contactPoints_.set(index, value);
        onChanged();
        return this;
      }
      /**
       * <code>repeated string contactPoints = 1;</code>
       */
      public Builder addContactPoints(
          java.lang.String value) {
        if (value == null) {
    throw new NullPointerException();
  }
  ensureContactPointsIsMutable();
        contactPoints_.add(value);
        onChanged();
        return this;
      }
      /**
       * <code>repeated string contactPoints = 1;</code>
       */
      public Builder addAllContactPoints(
          java.lang.Iterable<java.lang.String> values) {
        ensureContactPointsIsMutable();
        super.addAll(values, contactPoints_);
        onChanged();
        return this;
      }
      /**
       * <code>repeated string contactPoints = 1;</code>
       */
      public Builder clearContactPoints() {
        contactPoints_ = akka.protobuf.LazyStringArrayList.EMPTY;
        bitField0_ = (bitField0_ & ~0x00000001);
        onChanged();
        return this;
      }
      /**
       * <code>repeated string contactPoints = 1;</code>
       */
      public Builder addContactPointsBytes(
          akka.protobuf.ByteString value) {
        if (value == null) {
    throw new NullPointerException();
  }
  ensureContactPointsIsMutable();
        contactPoints_.add(value);
        onChanged();
        return this;
      }

      // @@protoc_insertion_point(builder_scope:Contacts)
    }

    static {
      defaultInstance = new Contacts(true);
      defaultInstance.initFields();
    }

    // @@protoc_insertion_point(class_scope:Contacts)
  }

  private static akka.protobuf.Descriptors.Descriptor
    internal_static_Contacts_descriptor;
  private static
    akka.protobuf.GeneratedMessage.FieldAccessorTable
      internal_static_Contacts_fieldAccessorTable;

  public static akka.protobuf.Descriptors.FileDescriptor
      getDescriptor() {
    return descriptor;
  }
  private static akka.protobuf.Descriptors.FileDescriptor
      descriptor;
  static {
    java.lang.String[] descriptorData = {
      "\n\033ClusterClientMessages.proto\"!\n\010Contact" +
      "s\022\025\n\rcontactPoints\030\001 \003(\tB$\n akka.cluster" +
      ".client.protobuf.msgH\001"
    };
    akka.protobuf.Descriptors.FileDescriptor.InternalDescriptorAssigner assigner =
      new akka.protobuf.Descriptors.FileDescriptor.InternalDescriptorAssigner() {
        public akka.protobuf.ExtensionRegistry assignDescriptors(
            akka.protobuf.Descriptors.FileDescriptor root) {
          descriptor = root;
          internal_static_Contacts_descriptor =
            getDescriptor().getMessageTypes().get(0);
          internal_static_Contacts_fieldAccessorTable = new
            akka.protobuf.GeneratedMessage.FieldAccessorTable(
              internal_static_Contacts_descriptor,
              new java.lang.String[] { "ContactPoints", });
          return null;
        }
      };
    akka.protobuf.Descriptors.FileDescriptor
      .internalBuildGeneratedFileFrom(descriptorData,
        new akka.protobuf.Descriptors.FileDescriptor[] {
        }, assigner);
  }

  // @@protoc_insertion_point(outer_class_scope)
}
