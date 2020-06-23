Taken from: https://web.archive.org/web/20130903082838/http://www.datadroidlib.com/presentation

     The Activity create a Request (in this example using a RequestFactory).
    The Request object contains the following data :
        A request type (integer) allowing you to distinguish the different type of Requests you have.
        A boolean to know whether to save the data received from the Operation in the memory cache or not
        Parameters for the request (key => value pattern)
    The Activity calls the execute() method of RequestManager.
    In the method, a check is automatically made to see if the same request call is already in progress.
        If it’s the case, the given RequestListener is added to the current request call.
        Otherwise a new request call is also created.
    The RequestManager forwards the Request to the RequestService if it's not already in progress.
    The RequestService creates the Operation corresponding to the received request type and gives it the Request.
    The Operation uses the provided NetworkConnection class to make the webservice request and parse the result.
        If a connection error occurs during the webservice call, it is sent back to the RequestManager. And it will be transmitted to the RequestListener (more information about that below).
        Same thing for a data error while parsing the data or saving the data in the database
        You can also if needed throw a CustomRequestException. In this case, you are getting called back in the RequestService if you have overridden the method onCustomRequestException().
        This allows you to provide additional information based on the exception you threw. As for the connection error, it is sent back the RequestManager and then to the RequestListener.
    Depending on your case, you can then either save your data in a ContentProvider or send it back to the calling Activity using Parcelable objects.
        If you save your data in a ContentProvider, you can use a CursorLoader in your Activity to retrieve your data.
        If you use Parcelable objects, you just have to add them to the returned Bundle.
        You should also set the flag in your Request to cache your data in memory.
    Finally a RequestListener callback is called from the RequestManager :
        onRequestFinished(request, resultData) if the request was executed without problems.
        onRequestConnectionError(request, statusCode) if a connection error occurred while executing the request.
        onRequestDataError(request) if a data error occurred while executing the request.
        onRequestCustomError(request, resultData) if a CustomRequestException was thrown during the execution of the request.
        in this case, resultData contains the additional information provided if you have overridden the method onCustomRequestException().

If your Activity was not listening anymore when the request finished its execution (for example a phone call is currently occurring), you will not be called back as you have unregistered your listener in the onPause() method.
On your next onResume() when you want to register again your listener, you can check if your request is still in progress using the isRequestInProgress(request) method of RequestManager.

    If that's the case, just register again your listener.
    Otherwise, if you have enabled the memory cache for your request (ie you are using Parcelable objects), you can use the callListenerWithCachedData(requestListener, request) method.
        If there is some cached data available, the method onRequestFinished(request, resultData) of the listener will be called with the cached data.
        Otherwise the method onRequestConnectionError(request, statusCode) will be called.