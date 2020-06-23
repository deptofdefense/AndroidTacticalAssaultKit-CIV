using System;
using System.Collections.Generic;
using System.Collections.ObjectModel;
using System.Diagnostics;
using System.IO;
using System.Linq;
using System.Net.NetworkInformation;
using System.Runtime.InteropServices;
using System.Runtime.InteropServices.WindowsRuntime;
using System.Text;
using System.Threading;
using System.Xml;
using Windows.Devices.Geolocation;
using Windows.Foundation;
using Windows.Foundation.Collections;
using Windows.UI.Core;
using Windows.UI.Xaml;
using Windows.UI.Xaml.Controls;
using Windows.UI.Xaml.Controls.Primitives;
using Windows.UI.Xaml.Data;
using Windows.UI.Xaml.Input;
using Windows.UI.Xaml.Media;
using Windows.UI.Xaml.Navigation;
using TAK.Commo;

// The Blank Page item template is documented at http://go.microsoft.com/fwlink/?LinkId=402352&clcid=0x409

namespace commoappcx
{
    /// <summary>
    /// An empty page that can be used on its own or navigated to within a Frame.
    /// </summary>
    public sealed partial class MainPage : ICommoLogger, IContactPresenceListener, ICoTMessageListener, IInterfaceStatusListener, IMissionPackageIO //: IContactPresenceListener, ICoTMessageListener, ICommoLogger, IInterfaceStatusListener, IMissionPackageIO
    {
        private readonly Commo _commo;
        private readonly Geolocator _geolocator;
        private Geocoordinate _position;
        private readonly DispatcherTimer _saTimer;
        private readonly string _myUid;
        private readonly string _myCallsign;

        private readonly Dictionary<NetInterface, string> _broadcastInterfaceDescriptions = new Dictionary<NetInterface, string>();
        private readonly Dictionary<NetInterface, string> _inboundInterfaceDescriptions = new Dictionary<NetInterface, string>();
        private readonly Dictionary<NetInterface, string> _streamingInterfaceDescriptions = new Dictionary<NetInterface, string>();

        public ObservableCollection<string> Contacts { get; } = new ObservableCollection<string>();
        public ObservableCollection<string> LogMessages { get; } = new ObservableCollection<string>(); 

        public ObservableCollection<string> Interfaces { get; } = new ObservableCollection<string>(); 

        public MainPage()
        {
            Debug.WriteLine("MainPage ctor");
            InitializeComponent();

            DataContext = this;

            try
            {
                _myUid = Guid.NewGuid().ToString();
                _myCallsign = "UWP Test App";
                _commo = new Commo(this, _myUid, _myCallsign);
                _commo.addCoTMessageListener(this);
                _commo.addContactPresenceListener(this);
                _commo.addInterfaceStatusListener(this);
                _commo.enableMissionPackageIO(this, 8080);
                MainText.Text = "Waiting for CoT Traffic...";

                InitCommoInterfaces();

                _geolocator = new Geolocator {DesiredAccuracy = PositionAccuracy.High};
                _geolocator.PositionChanged += _geolocator_PositionChanged;

                _saTimer = new DispatcherTimer {Interval = TimeSpan.FromSeconds(5)};
                _saTimer.Tick += SaTimerCallback;
                _saTimer.Start();
            }
            catch (Exception ex)
            {
                Debug.WriteLine(ex.Message);
            }
            Debug.WriteLine("End of MainPage ctor");
        }

        private void _geolocator_PositionChanged(Geolocator sender, PositionChangedEventArgs args)
        {
            _position = args.Position.Coordinate;
        }

        private void InitCommoInterfaces()
        {
            CoTMessageType[] messageTypes = { CoTMessageType.Chat, CoTMessageType.SituationalAwareness };

            var adapters = AdaptersHelper.GetAdapters();

            foreach (var adapter in adapters)
            {
                var macAddrBytes = new byte[6];
                for (int i = 0; i < 6; i++)
                    macAddrBytes[i] = adapter.MAC[i];
                var uri = new Uri("udp://239.2.3.1:6969");

                AddBroadcastInterface(uri, adapter, macAddrBytes, messageTypes);
                
                uri = new Uri("tcp://0:4242");
                AddInboundInterface(uri, adapter, macAddrBytes);
                uri = new Uri("udp://239.23.212.230:18999");
                AddInboundInterface(uri, adapter, macAddrBytes);
                uri = new Uri("udp://227.8.135.15:18200");
                AddInboundInterface(uri, adapter, macAddrBytes);
                uri = new Uri("udp://239.2.3.1:6969");
                AddInboundInterface(uri, adapter, macAddrBytes);
                uri = new Uri("udp://224.67.111.84:18000");
                AddInboundInterface(uri, adapter, macAddrBytes);
                uri = new Uri("udp://0:10011");
                AddInboundInterface(uri, adapter, macAddrBytes);
                uri = new Uri("udp://0:8087");
                AddInboundInterface(uri, adapter, macAddrBytes);
                uri = new Uri("udp://224.10.10.1:17012");
                AddInboundInterface(uri, adapter, macAddrBytes);
                uri = new Uri("udp://224.10.10.1:18740");
                AddInboundInterface(uri, adapter, macAddrBytes);
            }

            var cert = File.ReadAllBytes("testClient-demo.p12");
            var caCert = File.ReadAllBytes("truststore-demo.p12");
            var streamingUri = new Uri("ssl://demo.atakserver.com:1501");
            AddStreamingInterface(streamingUri, messageTypes, cert, caCert);
        }

        private void AddBroadcastInterface(Uri uri, AdapterInfo adapter, byte[] macAddrBytes, CoTMessageType[] messageTypes)
        {
            var broadcastInterface = _commo.addBroadcastInterface(macAddrBytes, messageTypes, uri.Host, uri.Port);
            if (broadcastInterface != null)
            {
                string key = uri.OriginalString + "::" + adapter.Name;
                _broadcastInterfaceDescriptions.Add(broadcastInterface, key);
            }
        }

        private void AddInboundInterface(Uri uri, AdapterInfo adapter, byte[] macAddrBytes)
        {
            string[] mcastUris = { uri.Host };
            var inboundInterface = _commo.addInboundInterface(macAddrBytes, uri.Port, mcastUris);
            if (inboundInterface != null)
            {
                string key = uri.OriginalString + "::" + adapter.Name;
                _inboundInterfaceDescriptions.Add(inboundInterface, key);
            }
        }

        private void AddStreamingInterface(Uri streamingUri, CoTMessageType[] messageTypes, byte[] cert, byte[] caCert)
        {
            var streamingInterface = _commo.addStreamingInterface(streamingUri.Host, streamingUri.Port, messageTypes, cert,
                caCert, "atakatak", "", "");
            if (streamingInterface != null)
            {
                string key = streamingUri.OriginalString;
                _streamingInterfaceDescriptions.Add(streamingInterface, key);
            }
        }

        static int speedSerial = 0;

        private async void SaTimerCallback(object sender, object state)
        {
            if (_commo != null)
            {
                if(_position == null)
                {
                    var geoPosition = await _geolocator.GetGeopositionAsync();
                    _position = geoPosition.Coordinate;
                }

                string now = DateTime.UtcNow.ToString("yyyy-MM-ddTHH:mm:ss.ffZ");
                string stale = DateTime.UtcNow.AddMinutes(2).ToString("yyyy-MM-ddTHH:mm:ss.ffZ");
                string saCotMessage = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><event version=\"2.0\" uid=\"";
                saCotMessage += _myUid;
                saCotMessage += "\" type=\"a-f-G-U-C\" time=\"";
                saCotMessage += now;
                saCotMessage += "\" start=\"";
                saCotMessage += now;
                saCotMessage += "\" stale=\"";
                saCotMessage += stale;
                saCotMessage += "\" how=\"h-e\">";
                saCotMessage += "<point lat=\"";
                saCotMessage += _position.Latitude.ToString("F8");
                saCotMessage += "\" lon=\"";
                saCotMessage += _position.Longitude.ToString("F8");
                saCotMessage += "\" hae=\"9999999.0\" ce=\"9999999\" le=\"9999999\"/>";
                saCotMessage += "<detail><contact phone=\"3152545187\" endpoint=\"10.233.154.103:4242:tcp\" callsign=\"";
                saCotMessage += _myCallsign;
                saCotMessage += "\"/><uid Droid=\"JDOG\"/><__group name=\"Cyan\" role=\"Team Member\"/><status battery=\"100\"/><track speed=\"";
                saCotMessage += speedSerial.ToString();
                speedSerial++;
                saCotMessage += "\" course=\"56.23885995781046\"/>";
                saCotMessage += "<precisionlocation geopointsrc=\"User\" altsrc=\"???\"/>";
                saCotMessage += "</detail>";
                saCotMessage += "</event>";

                _commo.broadcastCoT(saCotMessage);
            }
        }

        public async void contactAdded(string c)
        {
            await Dispatcher.RunAsync(CoreDispatcherPriority.Normal, () =>
            {
                Contacts.Add(c);
            });
        }

        public async void contactRemoved(string c)
        {
            await Dispatcher.RunAsync(CoreDispatcherPriority.Normal, () =>
            {
                if (Contacts.Contains(c))
                    Contacts.Remove(c);
            });
        }

        public async void cotMessageReceived(string cotMessage)
        {
            var cotDoc = new XmlDocument();
            cotDoc.LoadXml(cotMessage);
            await Dispatcher.RunAsync(CoreDispatcherPriority.Normal, () =>
            {
                using (var stringWriter = new StringWriter())
                using (var xmlTextWriter = XmlWriter.Create(stringWriter, new XmlWriterSettings { Indent = true }))
                {
                    cotDoc.WriteTo(xmlTextWriter);
                    xmlTextWriter.Flush();
                    MainText.Text = stringWriter.GetStringBuilder().ToString();
                }
            });
        }

        public async void log(Level level, string message)
        {
            await Dispatcher.RunAsync(CoreDispatcherPriority.Normal, () =>
            {
                LogMessages.Add(message);
            });
            Debug.WriteLine(message);
        }

        public async void interfaceUp(NetInterface iface)
        {
            Debug.WriteLine("interfaceUp");

            if (_broadcastInterfaceDescriptions.ContainsKey(iface))
            {
                string value = _broadcastInterfaceDescriptions[iface];
                await Dispatcher.RunAsync(CoreDispatcherPriority.Normal, () =>
                {
                    Interfaces.Add("Broadcast: " + value);
                });
            }
            if (_inboundInterfaceDescriptions.ContainsKey(iface))
            {
                string value = _inboundInterfaceDescriptions[iface];
                await Dispatcher.RunAsync(CoreDispatcherPriority.Normal, () =>
                {
                    Interfaces.Add("Inbound: " + value);
                });
            }
            if (_streamingInterfaceDescriptions.ContainsKey(iface))
            {
                string value = _streamingInterfaceDescriptions[iface];
                await Dispatcher.RunAsync(CoreDispatcherPriority.Normal, () =>
                {
                    Interfaces.Add("Streaming: " + value);
                });
            }
        }

        public async void interfaceDown(NetInterface iface)
        {
            Debug.WriteLine("interfaceDown");

            if (_broadcastInterfaceDescriptions.ContainsKey(iface))
            {
                string value = _broadcastInterfaceDescriptions[iface];
                string interfaceDesc = "Broadcast: " + value;
                await Dispatcher.RunAsync(CoreDispatcherPriority.Normal, () =>
                {
                    if (Interfaces.Contains(interfaceDesc))
                        Interfaces.Remove(interfaceDesc);
                });
            }
            if (_inboundInterfaceDescriptions.ContainsKey(iface))
            {
                string value = _inboundInterfaceDescriptions[iface];
                string interfaceDesc = "Inbound: " + value;
                await Dispatcher.RunAsync(CoreDispatcherPriority.Normal, () =>
                {
                    if (Interfaces.Contains(interfaceDesc))
                        Interfaces.Remove(interfaceDesc);
                });
            }
            if (_streamingInterfaceDescriptions.ContainsKey(iface))
            {
                string value = _streamingInterfaceDescriptions[iface];
                string interfaceDesc = "Streaming: " + value;
                await Dispatcher.RunAsync(CoreDispatcherPriority.Normal, () =>
                {
                    if (Interfaces.Contains(interfaceDesc))
                        Interfaces.Remove(interfaceDesc);
                });
            }
        }

        public MissionPackageTransferStatus missionPackageReceiveInit(string destFileIn, out string destFileOut, string transferName, string sha256hash,
            string senderCallsign)
        {
            var destFileInInfo = new FileInfo(destFileIn);
            var storageFolder = Windows.Storage.ApplicationData.Current.LocalFolder;
            destFileOut = Path.Combine(storageFolder.Path, destFileInInfo.Name);

            return MissionPackageTransferStatus.TransferSuccess;
        }

        public void missionPackageReceiveComplete(string destFile, MissionPackageTransferStatus status, string error)
        {
            throw new NotImplementedException();
        }

        public void missionPackageSendStatus(MissionPackageTransferStatusUpdate update)
        {
            throw new NotImplementedException();
        }

        public CoTPointData getCurrentPoint()
        {
            throw new NotImplementedException();
        }

        [DllImport("iphlpapi.dll", CharSet = CharSet.Ansi, ExactSpelling = true)]
        public static extern int GetAdaptersInfo(IntPtr pAdapterInfo, ref Int64 pBufOutLen);
    }
}
