package e_and_y.emergencyhelp;

import android.app.Activity;
import android.app.AlertDialog;
import android.support.v4.app.Fragment;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.telephony.PhoneNumberUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.ListView;

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Set;
import java.util.TreeSet;


public class SettingsFragmentPhones
        extends
            Fragment
        implements
            View.OnClickListener,
            AdapterView.OnItemClickListener
{
    private static final String LOG_TAG = "e.y/S...FragmentPhones";

    /**
     * mEmergencyContactsAdapter holds a reference to mEmergencyContacts. So changes from adapter
     * causes changes in mEmergencyContacts. The opposite is also true. IT MAY BE CAUSE OF BUGS.
     * For this reason mEmergencyContacts can be only List, but not Set.
     */
    private LinkedList<String> mEmergencyContacts;
    private ArrayAdapter<String> mEmergencyContactsAdapter;
    private final Set<String> mEmergencyPhoneNumbers = new TreeSet<>();

    private View mFragmentView;
    private AutoCompleteTextView mAddPhoneTextView;

    private AutoSummaryPreferenceFragment.OnSettingsChanged mActivityNotifier;
    private SharedPreferences mSharedPreferences;
    private boolean mWereSettingsChanged = false;


    /* I'm using here getActivity() method because I know that activity will not be recreated
     * after screen rotation. It's not safe in case of retaining the fragment */


    @Override
    public void onAttach(final Activity activity) {
        super.onAttach(activity);
        Log.d(LOG_TAG, "onAttach");

        try {
            mActivityNotifier = (AutoSummaryPreferenceFragment.OnSettingsChanged) activity;
        } catch (ClassCastException e) {
            throw new ClassCastException(activity.toString() + " must implement OnSettingsChanged");
        }
    }

    //1 first one
    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(LOG_TAG, "onCreate");

        mSharedPreferences = getActivity()
                .getApplicationContext()
                .getSharedPreferences(
                        SettingsActivity.PREFERENCES_FILENAME,
                        Context.MODE_MULTI_PROCESS);
    }

    //2 second one
    @Override
    public View onCreateView(
            final LayoutInflater inflater,
            final ViewGroup container,
            final Bundle savedInstanceState
    ) {
        Log.d(LOG_TAG, "onCreateView");

        if (mFragmentView == null) {
            mFragmentView = inflater.inflate(R.layout.fragment_phones_settings, null);

            final ListView mContactsListView = (ListView) mFragmentView.findViewById(R.id.contactList);
            mContactsListView.setOnItemClickListener(this);

            LayoutInflater inflaterHeader = (LayoutInflater) getActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            final View headerView = inflaterHeader.inflate(R.layout.header_in_fragment_phones_settings, null, false);

            headerView.findViewById(R.id.add_phone_button).setOnClickListener(this);    //TODO: crash sometimes

            mAddPhoneTextView = (AutoCompleteTextView) headerView.findViewById(R.id.new_phone_number);
            mAddPhoneTextView.setAdapter(new ArrayAdapter<String>(
                                                                getActivity(),
                                                                android.R.layout.simple_list_item_1,
                                                                getAllUserContactsAsArray()) );

            mEmergencyContacts = getEmergencyContactsFromPreferences();
            mEmergencyContactsAdapter = new ArrayAdapter<String>(
                                                                getActivity(),
                                                                android.R.layout.simple_list_item_1,
                                                                mEmergencyContacts);
            for (final String contact : mEmergencyContacts) {
                mEmergencyPhoneNumbers.add(getStripedPhoneNumberFromContact(contact));
            }

            mContactsListView.addHeaderView(headerView);
            mContactsListView.setAdapter(mEmergencyContactsAdapter);

//            TODO: Autocomplete сделать как-то поумнее, по подстрокам искать или как-то изменить вывод номера
        }
        return mFragmentView;
    }

    //3 third one
    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        Log.d(LOG_TAG, "onActivityCreate");

    }

    @Override
    public void onPause() {
        super.onPause();
        Log.d(LOG_TAG, "onPause");

        if (mWereSettingsChanged) {
            mActivityNotifier.onSettingsChanged();
            mWereSettingsChanged = false;
        }
    }

    @Override
    public void onClick(final View view) {
        Log.d(LOG_TAG, "onClick");

        final String contact = mAddPhoneTextView.getText().toString();
        addContact(contact);
    }

    @Override
    public void onItemClick(
            final AdapterView<?> adapterView,
            final View view,
            final int id,
            final long position
    ) {
        Log.d(LOG_TAG, "onItemClick");

        new AlertDialog.Builder(getActivity())
                .setMessage(getString(R.string.settings_confirm_deleting_number_text))
                .setPositiveButton(
                        getString(R.string.settings_confirm_deleting_number_positive_button),
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                deleteContactOnPosition((int) position);
                                dialogInterface.dismiss();
                            }
                        }
                )
                .setNegativeButton(
                        getString(R.string.settings_confirm_deleting_number_negative_button),
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                dialogInterface.cancel();
                            }
                        }
                )
                .create()
                .show();
    }


    private void addContact(final String contact) {
        final String number = getStripedPhoneNumberFromContact(contact);

        if (PhoneNumberUtils.isWellFormedSmsAddress(number)) {
            if ( ! mEmergencyPhoneNumbers.contains(number)) {
                mEmergencyContacts.add(contact);
                mEmergencyPhoneNumbers.add(number);

                saveEmergencyContactsInPreferences();

                mAddPhoneTextView.setText("");
                mEmergencyContactsAdapter.notifyDataSetChanged();
            } else {
                MainActivity.showOneButtonDialog(
                        getActivity(),
                        getString(R.string.settings_repeating_number),
                        getString(R.string.dialog_one_button_positive_button));
            }
        } else {
            MainActivity.showOneButtonDialog(
                    getActivity(),
                    getString(R.string.settings_invalid_number),
                    getString(R.string.dialog_one_button_positive_button));
        }
    }

    private void deleteContactOnPosition(final int position) {
        final String contact = mEmergencyContactsAdapter.getItem(position);
        final String number = getStripedPhoneNumberFromContact(contact);

        mEmergencyContactsAdapter.remove(contact);
        mEmergencyPhoneNumbers.remove(number);

        saveEmergencyContactsInPreferences();
    }

    private void saveEmergencyContactsInPreferences() {
        Log.d(LOG_TAG, "saveEmergencyContactsInPreferences");

        mSharedPreferences
                .edit()
                .putStringSet("emergency_contacts", new HashSet<String>(mEmergencyContacts))
                .apply();

        mWereSettingsChanged = true;
    }

    private LinkedList<String> getEmergencyContactsFromPreferences() {
        final Set<String> contacts = mSharedPreferences
                .getStringSet("emergency_contacts", new TreeSet<String>());

        return new LinkedList<String>(contacts);
    }

    private String[] getAllUserContactsAsArray() {
        final LinkedList<String> result  = new LinkedList<String>();

        final ContentResolver contentResolver = getActivity().getContentResolver();
        final Cursor contacts =
               contentResolver.query(ContactsContract.Contacts.CONTENT_URI, null, null, null, null);

        if (contacts.getCount() > 0) {
            final int idIndex = contacts.getColumnIndex(ContactsContract.Contacts._ID);
            final int nameIndex = contacts.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME);
            final int hasPhoneIndex =
                    contacts.getColumnIndex(ContactsContract.Contacts.HAS_PHONE_NUMBER);

            while (contacts.moveToNext()) {
                final String id = contacts.getString(idIndex);
                final String name = contacts.getString(nameIndex);

                if (Integer.parseInt(contacts.getString(hasPhoneIndex)) > 0) {
                    final Cursor numbers = contentResolver.query(
                            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                            null,
                            ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " = ?",
                            new String[]{id}, null);

                    final int phoneNumberIndex =
                            numbers.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER);

                    while (numbers.moveToNext()) {
                        final String phoneNumber = numbers.getString(phoneNumberIndex);

                        result.add(name + '\n' + phoneNumber);
                    }
                    numbers.close();
                }
            }
        }
        contacts.close();

        result.addAll(Arrays.asList(getResources().getStringArray(R.array.emergency_numbers)));

        return result.toArray(new String[result.size()]);
    }


    public static String getStripedPhoneNumberFromContact(final String contact) {
        final int separatorIndex = contact.lastIndexOf('\n');
        if ( separatorIndex != -1 ) {
            return PhoneNumberUtils.stripSeparators(contact.substring(separatorIndex + 1));
        } else {
            return contact;
        }
    }
}