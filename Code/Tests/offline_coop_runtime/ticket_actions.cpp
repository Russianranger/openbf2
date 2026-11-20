// [CHOSEN] QA-only trigger for the real local-player suicide action.
// No ticket getter, setter, event payload, or game rule is replaced here.
#include <Python.h>
#include "Code/BF2/Game/IGameLogic.h"

static PyObject* suicide(PyObject*, PyObject*)
{
    dice::hfe::gameLogic->suicide();
    Py_INCREF(Py_None);
    return Py_None;
}

static PyMethodDef methods[] = {
    {const_cast<char*>("suicide"), suicide, METH_NOARGS, 0}, {0, 0, 0, 0}
};
extern "C" void initticket_actions()
{
    Py_InitModule(const_cast<char*>("ticket_actions"), methods);
}
