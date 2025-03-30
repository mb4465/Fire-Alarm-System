package com.example.firealarmsystem.ui.gallery

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.firealarmsystem.R
import com.example.firealarmsystem.databinding.FragmentGalleryBinding
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase


class GalleryFragment : Fragment() {

    private var _binding: FragmentGalleryBinding? = null
    private val binding get() = _binding!!
    private lateinit var database: DatabaseReference
    private lateinit var macAddress: String
    private lateinit var oldPinDigitEditTexts: Array<EditText>
    private lateinit var newPinDigitEditTexts: Array<EditText>
    private lateinit var confirmPinDigitEditTexts: Array<EditText>

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentGalleryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Retrieve mac address and pin from where you store them
        // like sharedPreferences, arguments or from loginActivity
        // Here is a dummy initialization
        // you need to fetch these values form the login screen
        macAddress = "AA:BB:CC:DD:EE:FF" //Replace this with the correct mac Address

        initializeFirebase()
        setupPinDigitEditTexts()
        setupChangePinButton()
    }

    private fun initializeFirebase() {
        database = Firebase.database.reference
    }

    private fun setupPinDigitEditTexts() {
        oldPinDigitEditTexts = arrayOf(
            binding.etOldPinDigit1,
            binding.etOldPinDigit2,
            binding.etOldPinDigit3,
            binding.etOldPinDigit4
        )
        newPinDigitEditTexts = arrayOf(
            binding.etNewPinDigit1,
            binding.etNewPinDigit2,
            binding.etNewPinDigit3,
            binding.etNewPinDigit4
        )
        confirmPinDigitEditTexts = arrayOf(
            binding.etConfirmPinDigit1,
            binding.etConfirmPinDigit2,
            binding.etConfirmPinDigit3,
            binding.etConfirmPinDigit4
        )

        setupTextWatchers(oldPinDigitEditTexts)
        setupTextWatchers(newPinDigitEditTexts)
        setupTextWatchers(confirmPinDigitEditTexts)
    }

    private fun setupTextWatchers(pinEditTexts:Array<EditText>){
        for (i in pinEditTexts.indices) {
            pinEditTexts[i].addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                    if (s?.length == 1 && count == 0 ) {
                        if (i > 0){
                            pinEditTexts[i-1].requestFocus()
                        }
                    }
                }
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    if (s?.length == 1) {
                        if (i < pinEditTexts.size-1){
                            pinEditTexts[i+1].requestFocus()
                        }else {
                            pinEditTexts[i].clearFocus()
                        }

                    }
                }
                override fun afterTextChanged(s: Editable?) {}
            })
        }
    }


    private fun setupChangePinButton() {
        binding.btnChangePin.setOnClickListener {
            changePin()
        }
    }


    private fun changePin() {
        val oldPin =  getPinFromEditTexts(oldPinDigitEditTexts)
        val newPin = getPinFromEditTexts(newPinDigitEditTexts)
        val confirmNewPin = getPinFromEditTexts(confirmPinDigitEditTexts)



        if (oldPin.length != 4 || newPin.length != 4 || confirmNewPin.length != 4) {
            Toast.makeText(context, "Pin must have 4 digits", Toast.LENGTH_SHORT).show()
            return
        }


        if (newPin != confirmNewPin) {
            Toast.makeText(context, "New pin and confirm pin do not match", Toast.LENGTH_SHORT).show()
            return
        }
        // Build the path to the customer's data
        val customerPath = "/customers/$macAddress/pin"


        database.child(customerPath).get().addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val pin = task.result.getValue(String::class.java)
                if(pin == null || pin != oldPin){
                    Toast.makeText(context, "Incorrect Old Pin", Toast.LENGTH_SHORT).show()
                    return@addOnCompleteListener
                }
                updatePin(customerPath, confirmNewPin)
            }
            else{
                Log.w("Firebase", "Pin Change Failed: ", task.exception)
            }
        }

    }
    private fun getPinFromEditTexts(pinEditTexts:Array<EditText>): String {
        var pin = ""
        for(editText in pinEditTexts){
            pin += editText.text.toString()
        }
        return pin;
    }

    private fun updatePin(customerPath: String, newPin: String){
        database.child(customerPath).setValue(newPin)
            .addOnSuccessListener {
                Toast.makeText(context, "Pin changed successfully", Toast.LENGTH_SHORT).show()
                clearFields()
            }
            .addOnFailureListener { e ->
                Log.w("Firebase", "Pin Change Failed: ", e)
                Toast.makeText(context, "Failed to change pin.", Toast.LENGTH_SHORT).show()

            }
    }
    private fun clearFields(){
        for(editText in oldPinDigitEditTexts){
            editText.text?.clear()
        }
        for(editText in newPinDigitEditTexts){
            editText.text?.clear()
        }
        for(editText in confirmPinDigitEditTexts){
            editText.text?.clear()
        }
    }


    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}